package com.intelygenz.ifeedit.display;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.Html;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.intelygenz.ifeedit.R;
import com.intelygenz.ifeedit.content.ContentDownload;
import com.intelygenz.ifeedit.content.ItemContent;
import com.intelygenz.ifeedit.content.ItemStore;

/**
 * A list fragment representing a list of Items. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link ItemDetailFragment}.
 * <p/>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class ItemListFragment extends ListFragment {

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         * @param id The value of the key column "_id" in the database.
         */
        public void onItemSelected(int id);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(int id) {
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ItemListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: temp.
        ContentDownload cd = new ContentDownload();
        cd.generateContent("http://www.xatakandroid.com/tag/feeds/rss2.xml", new ItemStore(this.getContext()), new ContentDownload.Listener() {
            @Override
            public void onContentReady(boolean success) {
                Toast.makeText(getContext(), "DONE", Toast.LENGTH_SHORT).show();
                refreshFromDb();
            }
        });
    }

    /**
     * Updates the listed items by reading the current content in the database.
     */
    public void refreshFromDb() {
        // Close previous load.
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        // Query content.
        ItemStore database = new ItemStore(this.getContext());
        mCursor = database.get().query(ItemStore.DB_TABLE_NAME, ItemStore.DB_COLS, null, null, null, null, null);
        int[] to = new int[] { R.id.entry_title, R.id.entry_summary, R.id.entry_image};
        CustomCursorAdapter cca = new CustomCursorAdapter(this.getContext(), R.layout.activity_item_list_entry, mCursor, ItemStore.DB_COLS, to, 0);
        setListAdapter(cca);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        mCursor.moveToPosition((int) id);
        int dbId = mCursor.getInt(mCursor.getColumnIndex(ItemStore.DB_COL_ID));
        mCallbacks.onItemSelected(dbId);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        getListView().setChoiceMode(activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    /** The cursor to retrieve content from database. */
    private Cursor mCursor;

    /**
     * Presents the information in each cursor entry on the entry layout (a row in the item list).
     */
    private class CustomCursorAdapter extends SimpleCursorAdapter {
        public CustomCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView title = (TextView) view.findViewById(R.id.entry_title);
            TextView summary = (TextView) view.findViewById(R.id.entry_summary);
            ImageView image = (ImageView) view.findViewById(R.id.entry_image);
            title.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_TITLE))));
            summary.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_DESCRIPTION))));
            // TODO: image
        }
    }
}
