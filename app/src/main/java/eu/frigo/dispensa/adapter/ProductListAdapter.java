package eu.frigo.dispensa.adapter; // Crea un package adapter o simile

import android.annotation.SuppressLint;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.Product;

public class ProductListAdapter extends ListAdapter<Product, ProductListAdapter.ProductViewHolder> {

    private OnItemInteractionListener listener;
    private Product selectedProduct;

    public interface OnItemInteractionListener {
        void onEditProduct(Product product);
        void onDeleteProduct(Product product);
    }

    public ProductListAdapter(@NonNull DiffUtil.ItemCallback<Product> diffCallback, OnItemInteractionListener listener) {
        super(diffCallback);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_product, parent, false);
        return new ProductViewHolder(itemView);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product currentProduct = getItem(position);
        holder.bind(currentProduct);
        holder.itemView.setOnLongClickListener(v -> {
            selectedProduct = currentProduct;
            return false;
        });
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener{
        private final TextView textViewQuantity;
        private final TextView textViewExpiryDate;
        private final TextView textViewProductName;
        private final ImageView imageViewProduct;

        ProductViewHolder(View itemView) {
            super(itemView);
            textViewQuantity = itemView.findViewById(R.id.textViewItemQuantity);
            textViewExpiryDate = itemView.findViewById(R.id.textViewItemExpiryDate);
            textViewProductName = itemView.findViewById(R.id.textViewItemProductName);
            imageViewProduct = itemView.findViewById(R.id.imageViewItemProduct);
           itemView.setOnCreateContextMenuListener(this);
        }

        void bind(Product product) {
            if (product.getProductName() != null && !product.getProductName().isEmpty()) {
                textViewProductName.setText(product.getProductName());
                textViewProductName.setVisibility(View.VISIBLE);
            } else {
                textViewProductName.setText(product.getBarcode());
            }
            textViewQuantity.setText(itemView.getContext().getString(R.string.quantity_label, product.getQuantity()));
            textViewExpiryDate.setText(itemView.getContext().getString(R.string.expiry_date_label, product.getExpiryDate()));
            if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(product.getImageUrl())
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_placeholder_image)
                        .into(imageViewProduct);
                imageViewProduct.setVisibility(View.VISIBLE);
            } else {
                imageViewProduct.setImageResource(R.drawable.ic_placeholder_image);
            }        }
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            MenuItem edit = menu.add(Menu.NONE, R.id.action_edit_product, 1, "Modifica");
            MenuItem delete = menu.add(Menu.NONE, R.id.action_delete_product, 2, "Cancella");
        }
    }

    public Product getProductAt(int position) {
        return getItem(position);
    }

    // Metodo per ottenere il prodotto selezionato (usato dall'Activity)
    public Product getSelectedProduct() {
        return selectedProduct;
    }

    // DiffUtil per aggiornamenti efficienti della lista
    public static class ProductDiff extends DiffUtil.ItemCallback<Product> {
        @Override
        public boolean areItemsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Product oldItem, @NonNull Product newItem) {
            return oldItem.getBarcode().equals(newItem.getBarcode()) &&
                    oldItem.getQuantity() == newItem.getQuantity() &&
                    oldItem.getExpiryDate().equals(newItem.getExpiryDate());
        }
    }
}