package eu.frigo.dispensa.util;

import android.graphics.Canvas;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.frigo.dispensa.adapter.ReorderLocationsAdapter;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final ReorderLocationsAdapter mAdapter;
    private final ItemTouchHelperListener mListener;

    public interface ItemTouchHelperListener {
        void onOrderChanged(List<StorageLocation> orderedLocations);
    }

    public SimpleItemTouchHelperCallback(ReorderLocationsAdapter adapter, ItemTouchHelperListener listener) {
        mAdapter = adapter;
        mListener = listener;
    }

    @Override
    public boolean isLongPressDragEnabled() {return false;}

    @Override
    public boolean isItemViewSwipeEnabled() {return false;}

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Non usato
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            if (viewHolder != null) {
                viewHolder.itemView.setAlpha(0.7f);
            }
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        viewHolder.itemView.setAlpha(1.0f);
        // Salvataggio!
        if (mListener != null) {
            mListener.onOrderChanged(mAdapter.getCurrentOrder());
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
