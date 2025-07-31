package eu.frigo.dispensa.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.StorageLocation;

public class StorageLocationAdapter extends ListAdapter<StorageLocation, StorageLocationAdapter.StorageLocationViewHolder> {

    private OnLocationInteractionListener listener;

    public interface OnLocationInteractionListener {
        void onEditLocation(StorageLocation location);
        void onDeleteLocation(StorageLocation location);
        void onSetAsDefault(StorageLocation location); // Potresti voler un clic sull'item per questo
    }

    public StorageLocationAdapter(@NonNull DiffUtil.ItemCallback<StorageLocation> diffCallback, OnLocationInteractionListener listener) {
        super(diffCallback);
        this.listener = listener;
    }

    @NonNull
    @Override
    public StorageLocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_storage_location, parent, false);
        return new StorageLocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StorageLocationViewHolder holder, int position) {
        StorageLocation current = getItem(position);
        holder.bind(current, listener);
    }

    static class StorageLocationViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewLocationName;
        private final ImageView iconDefault;
        private final ImageView buttonEdit;
        private final ImageView buttonDelete;

        public StorageLocationViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewLocationName = itemView.findViewById(R.id.textView_location_name);
            iconDefault = itemView.findViewById(R.id.icon_location_default);
            buttonEdit = itemView.findViewById(R.id.button_edit_location);
            buttonDelete = itemView.findViewById(R.id.button_delete_location);
        }

        public void bind(final StorageLocation location, final OnLocationInteractionListener listener) {
            textViewLocationName.setText(location.name);
            iconDefault.setVisibility(location.isDefault ? View.VISIBLE : View.GONE);

            // Impedisci modifica/eliminazione delle predefinite per ora (opzionale)
            if (location.isPredefined) {
                buttonEdit.setVisibility(View.GONE); // O disabilita e cambia icona
                buttonDelete.setVisibility(View.GONE);
            } else {
                buttonEdit.setVisibility(View.VISIBLE);
                buttonDelete.setVisibility(View.VISIBLE);
            }

            buttonEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditLocation(location);
                }
            });

            buttonDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteLocation(location);
                }
            });

            // Click sull'intero item per impostare come default (esempio)
            itemView.setOnClickListener(v -> {
                if (listener != null && !location.isDefault) { // Solo se non è già default
                    listener.onSetAsDefault(location);
                }
            });
        }
    }

    public static class StorageLocationDiff extends DiffUtil.ItemCallback<StorageLocation> {
        @Override
        public boolean areItemsTheSame(@NonNull StorageLocation oldItem, @NonNull StorageLocation newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull StorageLocation oldItem, @NonNull StorageLocation newItem) {
            return oldItem.name.equals(newItem.name) &&
                    oldItem.isDefault == newItem.isDefault &&
                    oldItem.orderIndex == newItem.orderIndex && // Aggiungi altri campi se necessario
                    oldItem.isPredefined == newItem.isPredefined;
        }
    }
}
