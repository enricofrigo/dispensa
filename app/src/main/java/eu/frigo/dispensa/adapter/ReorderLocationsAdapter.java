package eu.frigo.dispensa.adapter;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class ReorderLocationsAdapter extends RecyclerView.Adapter<ReorderLocationsAdapter.ViewHolder> {

    private List<StorageLocation> locations;
    private final OnStartDragListener dragStartListener;
    private final OnLocationInteractionListener interactionListener;

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public interface OnLocationInteractionListener {
        void onEditLocation(StorageLocation location);
        void onDeleteLocation(StorageLocation location);
        void onSetAsDefault(StorageLocation location);

    }

    public ReorderLocationsAdapter(List<StorageLocation> locations,
                                   OnStartDragListener dragStartListener,
                                   OnLocationInteractionListener interactionListener) {
        this.locations = locations;
        this.dragStartListener = dragStartListener;
        this.interactionListener = interactionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reorderable_location, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility") // Per setOnTouchListener
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StorageLocation location = locations.get(position);
        Log.d("ReorderLocationsAdapter", position+"onBindViewHolder: "+location);
        holder.locationName.setText(location.getName());

        if (location.isPredefined()) {
            holder.editButton.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.GONE);
        } else {
            holder.editButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.editButton.setOnClickListener(v -> {
                if (interactionListener != null) {
                    interactionListener.onEditLocation(location);
                }
            });

            holder.deleteButton.setOnClickListener(v -> {
                if (interactionListener != null) {
                    interactionListener.onDeleteLocation(location);
                }
            });
        }
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (dragStartListener != null) {
                    dragStartListener.onStartDrag(holder);
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return locations == null ? 0 : locations.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(locations, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(locations, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<StorageLocation> getCurrentOrder() {
        return locations;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitList(List<StorageLocation> newLocations) {
        this.locations = newLocations;
        notifyDataSetChanged(); // Per semplicità, usa DiffUtil per un'app di produzione
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView locationName;
        ImageView dragHandle;
        ImageButton editButton;
        ImageButton deleteButton;
        ViewHolder(View itemView) {
            super(itemView);
            locationName = itemView.findViewById(R.id.textView_location_name_reorder);
            dragHandle = itemView.findViewById(R.id.imageView_drag_handle);
            editButton = itemView.findViewById(R.id.button_edit_location_item);
            deleteButton = itemView.findViewById(R.id.button_delete_location_item);
        }
    }
}
