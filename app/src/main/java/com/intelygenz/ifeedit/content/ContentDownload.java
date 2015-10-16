package com.intelygenz.ifeedit.content;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

        // Initiate the process in the background.
        (new RssXmlProcessor()).execute(rssUrl);
    }

    /** The content download initiator waiting for completion. */
    private Listener mListener;

    /** The database provided to fill in with the downloaded content. */
    private ItemStore mDatabase;

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
                    default:
                        skip(parser);
                        break;
                }
            }

            // Save the item content into database.
            ContentValues values = new ContentValues();
            values.put(ItemStore.DB_COL_TITLE, title);
            values.put(ItemStore.DB_COL_LINK, link);
            values.put(ItemStore.DB_COL_DESCRIPTION, description);
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
            String link = readText(parser); // TODO: extract image + description text.
            parser.require(XmlPullParser.END_TAG, null, "description");
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
    }
}
