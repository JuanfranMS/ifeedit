package com.intelygenz.ifeedit.display;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.intelygenz.ifeedit.R;
import com.intelygenz.ifeedit.content.ItemStore;

/**
 * A fragment representing a single Item detail screen.
 * This fragment is either contained in a {@link ItemListActivity}
 * in two-pane mode (on tablets) or a {@link ItemDetailActivity}
 * on handsets.
 */
public class ItemDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ItemDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_item_detail, container, false);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            // Load the content of the requested item in database.
            int id = getArguments().getInt(ARG_ITEM_ID);
            // TODO: share the ItemStore from activity and close it on activity destroy.
            Cursor cursor = (new ItemStore(getContext())).get().query(ItemStore.DB_TABLE_NAME, ItemStore.DB_COLS, "_id = " + id, null, null, null, null);
            String content = "No content";
            if (cursor.moveToFirst()) {
                content = cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_DESCRIPTION));
                // Remember this link to the cursor can be closed in the method.
                mLink = cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_LINK));
            }

            // Fill in the web view.
            WebView wb = (WebView) rootView.findViewById(R.id.detail_webview);
            wb.getSettings().setUseWideViewPort(false);
            wb.getSettings().setLoadWithOverviewMode(true); // Note: this line tries to fit images on screen, but not working.
            wb.loadData(content, "text/html; charset=utf-8", null);

            // Display the item title on top.
            Activity activity = this.getActivity();
            CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) activity.findViewById(R.id.toolbar_layout);
            if (appBarLayout != null) {
                appBarLayout.setTitle(cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_TITLE)));
            }
            cursor.close();

            // Launch browser floating button.
            FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab_fragment);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchBrowser();
                }
            });

        }
        return rootView;
    }

    /**
     * Launcher an external browser to let the user navigate into the item's source.
     */
    public void launchBrowser() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mLink)));
    }

    /** The URL to launch in the external browser. */
    private String mLink;
}
