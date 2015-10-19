package com.intelygenz.ifeedit.display;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.intelygenz.ifeedit.R;
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
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         * @param id The value of the key column "_id" in the database.
         */
        void onItemSelected(int id);
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
    }

    /**
     * Make the list of be cleared and a spinner appears until a refreshFromDb is called.
     */
    public void showLoadingIndicator() {
        setListShown(false);
    }

    /**
     * Updates the listed items by reading the current content in the database.
     * @param searchCondition Filters the content to be displayed in the list. A string to exist in the title.
     */
    public void refreshFromDb(String searchCondition) {
        // Close previous load.
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        // Query content from database and place in the list view using a cursor adapter.
        // Getting all (some, if search condition provided) entries stored in database from the most recent (publication date).
        setListShown(true);
        String whereFilter = searchCondition == null || searchCondition.isEmpty() ? null : ItemStore.DB_COL_TITLE + " like '%" + searchCondition + "%'";
        ItemStore database = new ItemStore(this.getContext());
        mCursor = database.get().query(ItemStore.DB_TABLE_NAME, ItemStore.DB_COLS, whereFilter, null, null, null, ItemStore.DB_COL_PUB_DATE + " DESC");
        int[] to = new int[] { R.id.entry_title, R.id.entry_summary, R.id.entry_image};
        CustomCursorAdapter cca = new CustomCursorAdapter(this.getContext(), R.layout.activity_item_list_entry, mCursor, ItemStore.DB_COLS, to, 0);
        setListAdapter(cca);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
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
        mCursor.moveToPosition(position);
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

            // Place title.
            title.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_TITLE))));

            // Build and place description summary.
            // The description content sometimes comes as plain text but also as HTML. Not easy to get a proper summary.
            String description = cursor.getString(cursor.getColumnIndex(ItemStore.DB_COL_DESCRIPTION));
            String summaryAttempt;
            int paragraph = description.indexOf("<p>", description.indexOf("<p>") + 3);
            if (paragraph != -1) summaryAttempt = description.substring(paragraph + 3);
            else summaryAttempt = description;
            summary.setText(Html.fromHtml(summaryAttempt));

            // Place the image.
            byte[] imageContent = cursor.getBlob(cursor.getColumnIndex(ItemStore.DB_COL_IMAGE_CONTENT));
            if (imageContent != null) image.setImageBitmap(BitmapFactory.decodeByteArray(imageContent, 0, imageContent.length));
            else image.setImageResource(R.mipmap.ic_launcher);
        }
    }
}
