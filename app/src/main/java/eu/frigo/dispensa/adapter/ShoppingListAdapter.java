package eu.frigo.dispensa.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;

public class ShoppingListAdapter extends ListAdapter<ShoppingItem, ShoppingListAdapter.ShoppingViewHolder> {

    private final OnShoppingItemInteractionListener listener;

    public interface OnShoppingItemInteractionListener {
        void onItemCheckedChanged(ShoppingItem item);
    }

    public ShoppingListAdapter(@NonNull DiffUtil.ItemCallback<ShoppingItem> diffCallback,
                               OnShoppingItemInteractionListener listener) {
        super(diffCallback);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShoppingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_shopping, parent, false);
        return new ShoppingViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ShoppingViewHolder holder, int position) {
        ShoppingItem item = getItem(position);
        holder.bind(item, listener);
    }

    static class ShoppingViewHolder extends RecyclerView.ViewHolder {

        private final TextView textViewName;
        private final TextView textViewQuantity;
        private final CheckBox checkBox;

        ShoppingViewHolder(View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewShoppingItemName);
            textViewQuantity = itemView.findViewById(R.id.textViewShoppingItemQuantity);
            checkBox = itemView.findViewById(R.id.checkBoxShoppingItem);
        }

        void bind(ShoppingItem item, OnShoppingItemInteractionListener listener) {
            textViewName.setText(item.getName());
            textViewQuantity.setText(itemView.getContext().getString(R.string.shopping_list_quantity, item.getQuantity()));

            // Imposta lo stato della checkbox senza trigger del listener
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(item.isChecked());

            // Stile visivo: barrato e attenuato se comprato
            if (item.isChecked()) {
                textViewName.setPaintFlags(textViewName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                textViewQuantity.setPaintFlags(textViewQuantity.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                itemView.setAlpha(0.5f);
            } else {
                textViewName.setPaintFlags(textViewName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                textViewQuantity.setPaintFlags(textViewQuantity.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                itemView.setAlpha(1.0f);
            }

            // Listener per la checkbox
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onItemCheckedChanged(item);
                }
            });

            // Tap sulla riga intera cambia lo stato
            itemView.setOnClickListener(v -> {
                checkBox.setChecked(!checkBox.isChecked());
            });
        }
    }

    public static class ShoppingItemDiff extends DiffUtil.ItemCallback<ShoppingItem> {
        @Override
        public boolean areItemsTheSame(@NonNull ShoppingItem oldItem, @NonNull ShoppingItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ShoppingItem oldItem, @NonNull ShoppingItem newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                    oldItem.getQuantity() == newItem.getQuantity() &&
                    oldItem.isChecked() == newItem.isChecked();
        }
    }
}
