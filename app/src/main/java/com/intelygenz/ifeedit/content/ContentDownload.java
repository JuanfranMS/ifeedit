package com.intelygenz.ifeedit.content;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Connects to an RSS URL extracting the channel's items and stores them in the internal database.
 */
public class ContentDownload {

    /**
     * To be notified when the content is already available in the database.
     */
    public interface Listener {
        /**
         * Called when the content download process has completed so that the channel items are
         * stored in the database.
         * @param success Whether the process completed successfully, otherwise the database will not contain any items.
         */
        void onContentReady(boolean success);
    }

    /**
     * Initiates the download and storage process.
     * This call returns immediately.
     * @param rssUrl RSS URL when the feed XML file is located.
     * @param database The item database where downloaded content will persist.
     * @param listener To receive the notification when process completes.
     */
    public void generateContent(String rssUrl, ItemStore database, Listener listener) {
        mListener = listener;
        mDatabase = database;

        // Clean up database removing previous content. TODO: avoid removing previous content if this download process fails (preserve previous content at lest).
        mDatabase.get().execSQL("DELETE FROM " + ItemStore.DB_TABLE_NAME);
        mEntryId = 0;

        // Initiate the process in the background.
        (new RssXmlProcessor()).execute(rssUrl);
    }

    /** The content download initiator waiting for completion. */
    private Listener mListener;

    /** The database provided to fill in with the downloaded content. */
    private ItemStore mDatabase;

    /** Counter to provide the primary key of the database table in its "_id" field, needed by cursors. */
    private int mEntryId;

    /**
     * Performs the process of downloading the RSS xml file, parse and store in database.
     * See http://www.w3schools.com/xml/xml_rss.asp for format specifications.
     * See http://developer.android.com/training/basics/network-ops/xml.html on how to parse an XML file.
     */
    private class RssXmlProcessor extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... url) {
            InputStream stream = null;
            try {
                // Open the input stream that provides the content.
                stream = downloadUrl(url[0]);

                // Parse the xml file.
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(stream, null);
                parser.nextTag();
                readRss(parser);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } catch (XmlPullParserException e) {
                e.printStackTrace();
                return false;
            } finally {
                if (stream != null) try { stream.close(); } catch (IOException e) { /* Give up. */ }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (mListener != null) mListener.onContentReady(success);
        }

        /**
         * Given a string representation of a URL, sets up a connection and gets an input stream.
         * @param urlString The URL whose content is about to be downloaded.
         * @return The content as an input stream ready to read.
         */
        private InputStream downloadUrl(String urlString) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();
            return conn.getInputStream();
        }

        /**
         * Processes one top level tag "rss" and so the entire content.
         */
        private void readRss(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "rss");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                // Looking for the "channel" tag.
                if (name.equals("channel")) readChannel(parser);
                else skip(parser);
            }
        }

        /**
         * Processes one "channel" tag.
         * The items inside will be processed and stored in database.
         */
        private void readChannel(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "channel");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                // Looking for the first/next "item" tag.
                if (name.equals("item")) readItem(parser);
                else skip(parser);
            }
        }

        /**
         * Helper method to go through a tag content that is useless for us.
         */
        private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
            if (parser.getEventType() != XmlPullParser.START_TAG) throw new IllegalStateException();
            int depth = 1;
            while (depth != 0) {
                switch (parser.next()) {
                    case XmlPullParser.END_TAG:
                        depth--;
                        break;
                    case XmlPullParser.START_TAG:
                        depth++;
                        break;
                }
            }
        }

        /**
         * Processes one "item" tag extracting its content and storing them in the database.
         */
        private void readItem(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "item");
            String title = null;
            String link = null;
            String description = null;
            String imageUrl = null;
            String pubDate = null;
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                switch (name) {
                    case "title":
                        title = readTitle(parser);
                        break;
                    case "link":
                        link = readLink(parser);
                        break;
                    case "description":
                        description = readDescription(parser);
                        break;
                    case "image":
                        imageUrl = readImage(parser);
                        break;
                    case "pubDate":
                        pubDate = readPubDate(parser);
                        break;
                    default:
                        skip(parser);
                        break;
                }
            }

            // Case of no image tag available. Try to extract an image URL from the description then.
            if (imageUrl == null && description != null) {
                int urlStart = description.indexOf("src=\"") + 5;
                int urlStop = description.substring(urlStart).indexOf(".jpg") + 4;
                imageUrl = description.substring(urlStart, urlStart + urlStop);
            }

            // Download the image (its content, not just the link) to be also stored in database.
            byte[] imageBlob = null;
            //if (imageUrl == null) imageUrl = "http://i.blogs.es/1fedef/play-store/650_1200.jpg"; // TODO: temp.
            if (imageUrl != null) {
                try {
                    final int MAX_IMAGE_CONTENT_SIZE = 1024 * 1024;
                    final int IMAGE_CHUNCK_SIZE = 1024;
                    InputStream imageStream = new URL(imageUrl).openConnection().getInputStream();
                    byte[] imageContent = new byte[MAX_IMAGE_CONTENT_SIZE]; // TODO: move to a class member for performance reasons.
                    int totalRead = 0;
                    int readBytes;
                    while ((readBytes = imageStream.read(imageContent, totalRead, IMAGE_CHUNCK_SIZE)) != -1 && totalRead < MAX_IMAGE_CONTENT_SIZE)
                        totalRead += readBytes;
                    imageBlob = new byte[totalRead];
                    for (int i = 0; i < totalRead; ++i) imageBlob[i] = imageContent[i];
                } catch (IOException e) {
                    // This may be ok if the attempt to get an image URL from the description fails.
                    Log.i("ContentDownload", "Failed to download image from " + imageUrl);
                }
            }

            // Convert date as received into long time-stamp.
            long timestamp = 0;
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
            try {
                Date date = formatter.parse(pubDate);
                timestamp = date.getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }

            // Save the item content into database.
            ContentValues values = new ContentValues();
            values.put(ItemStore.DB_COL_ID, mEntryId++);
            values.put(ItemStore.DB_COL_TITLE, title);
            values.put(ItemStore.DB_COL_LINK, link);
            values.put(ItemStore.DB_COL_DESCRIPTION, description);
            values.put(ItemStore.DB_COL_IMAGE_URL, imageUrl);
            values.put(ItemStore.DB_COL_PUB_DATE, timestamp);
            if (imageBlob != null) values.put(ItemStore.DB_COL_IMAGE_CONTENT, imageBlob);
            mDatabase.get().insert(ItemStore.DB_TABLE_NAME, null, values);
        }

        /**
         * Processes one "title" tag.
         */
        private String readTitle(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "title");
            String title = readText(parser);
            parser.require(XmlPullParser.END_TAG, null, "title");
            return title;
        }

        /**
         * Processes one "link" tag.
         */
        private String readLink(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "link");
            String link = readText(parser);
            parser.require(XmlPullParser.END_TAG, null, "link");
            return link;
        }

        /**
         * Processes one "description" tag.
         */
        private String readDescription(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "description");
            String description = readText(parser);
            parser.require(XmlPullParser.END_TAG, null, "description");
            return description;
        }

        /**
         * Processes one "pubDate" tag.
         */
        private String readPubDate(XmlPullParser parser) throws IOException, XmlPullParserException {
            parser.require(XmlPullParser.START_TAG, null, "pubDate");
            String link = readText(parser);
            parser.require(XmlPullParser.END_TAG, null, "pubDate");
            return link;
        }

        /**
         * Extracts the text content of title, link and description.
         */
        private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
            String result = "";
            if (parser.next() == XmlPullParser.TEXT) {
                result = parser.getText();
                parser.nextTag();
            }
            return result;
        }

        /**
         * Processes one "image" tag.
         * The "url" tag inside will be processed to get the item's image (may not be present).
         * @return The URL from where the image can be downloaded (might be null).
         */
        private String readImage(XmlPullParser parser) throws IOException, XmlPullParserException {
            String imageUrl = null;
            parser.require(XmlPullParser.START_TAG, null, "image");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                if (name.equals("url")) imageUrl = readText(parser);
                else skip(parser);
            }
            return imageUrl;
        }
    }
}
