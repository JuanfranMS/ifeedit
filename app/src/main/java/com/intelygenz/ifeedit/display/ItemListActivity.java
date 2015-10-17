package com.intelygenz.ifeedit.display;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.intelygenz.ifeedit.R;
import com.intelygenz.ifeedit.content.ContentDownload;
import com.intelygenz.ifeedit.content.ItemStore;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link ItemListFragment} and the item details
 * (if present) is a {@link ItemDetailFragment}.
 * <p/>
 * This activity also implements the required
 * {@link ItemListFragment.Callbacks} interface
 * to listen for item selections.
 */
public class ItemListActivity extends AppCompatActivity implements ItemListFragment.Callbacks {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_app_bar);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        // Settings button.
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ItemListActivity.this, SettingsActivity.class));
            }
        });

        if (findViewById(R.id.item_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/values-large and res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            // In two-pane mode, list items should be given the 'activated' state when touched.
            ((ItemListFragment) getSupportFragmentManager().findFragmentById(R.id.item_list)).setActivateOnItemClick(true);
        }

        // Fill in content when the app starts.
        ((ItemListFragment) getSupportFragmentManager().findFragmentById(R.id.item_list)).refreshFromDb();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check the current feed URL in preferences, it may have changed.
        final SharedPreferences activityPrefs = getSharedPreferences("ItemListActivity", Activity.MODE_PRIVATE);
        final String current = PreferenceManager.getDefaultSharedPreferences(this).getString("settings_feed_url", getString(R.string.pref_default_feed_url));
        String last = activityPrefs.getString(SETTINGS_FEED_URL, "");

        // Case of URL has not changed, do not re-download content.
        if (current.equals(last)) return;

        // Time to download new content from the new URL.
        ContentDownload cd = new ContentDownload();
        final ItemStore database = new ItemStore(this);
        cd.generateContent(current, database, new ContentDownload.Listener() {
            @Override
            public void onContentReady(boolean success) {
                database.close();
                // Show the new content in the item list.
                if (success) activityPrefs.edit().putString(SETTINGS_FEED_URL, current).apply();
                ((ItemListFragment) getSupportFragmentManager().findFragmentById(R.id.item_list)).refreshFromDb();
            }
        });
    }

    /**
     * Callback method from {@link ItemListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(int id) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putInt(ItemDetailFragment.ARG_ITEM_ID, id);
            ItemDetailFragment fragment = new ItemDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction() .replace(R.id.item_detail_container, fragment).commit();
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, ItemDetailActivity.class);
            detailIntent.putExtra(ItemDetailFragment.ARG_ITEM_ID, id);
            startActivity(detailIntent);
        }
    }

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    /** Local shared preference that remembers the last used URL. */
    private static final String SETTINGS_FEED_URL = "last_feed_url_loaded";
}
