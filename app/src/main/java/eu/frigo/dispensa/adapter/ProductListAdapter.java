package eu.frigo.dispensa.adapter; // Crea un package adapter o simile

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.ui.SettingsFragment;

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
        private final MaterialCardView cardProductItem;

        ProductViewHolder(View itemView) {
            super(itemView);
            textViewQuantity = itemView.findViewById(R.id.textViewItemQuantity);
            textViewExpiryDate = itemView.findViewById(R.id.textViewItemExpiryDate);
            textViewProductName = itemView.findViewById(R.id.textViewItemProductName);
            imageViewProduct = itemView.findViewById(R.id.imageViewItemProduct);
            cardProductItem = itemView.findViewById(R.id.card_product_item);
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

            textViewExpiryDate.setText(itemView.getContext().getString(R.string.expiry_date_label, product.getExpiryDateString()));
            if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(product.getImageUrl())
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_placeholder_image)
                        .into(imageViewProduct);
                imageViewProduct.setVisibility(View.VISIBLE);
            } else {
                imageViewProduct.setImageResource(R.drawable.ic_placeholder_image);
            }
            
            if (product.getExpiryDate() != null) {
                textViewExpiryDate.setText(String.format("Scad: %s", eu.frigo.dispensa.utils.DateConverter.formatTimestampToDisplayDate(product.getExpiryDate())));
                textViewExpiryDate.setVisibility(View.VISIBLE);

                long todayTimestamp = eu.frigo.dispensa.utils.DateConverter.getTodayNormalizedTimestamp(); // Mezzogiorno di oggi

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(itemView.getContext());
                String daysBeforeStr = prefs.getString(SettingsFragment.KEY_EXPIRY_DAYS_BEFORE, "3");
                int daysBeforeWarning;
                try {
                    daysBeforeWarning = Integer.parseInt(daysBeforeStr);
                } catch (NumberFormatException e) {
                    daysBeforeWarning = 3; // Fallback
                }

                Calendar warningCalendar = Calendar.getInstance();
                warningCalendar.setTimeInMillis(todayTimestamp);
                warningCalendar.add(Calendar.DAY_OF_YEAR, daysBeforeWarning);
                long warningTimestamp = warningCalendar.getTimeInMillis(); // Timestamp per l'inizio del periodo di "avviso"

                // Stato 1: Scaduto
                if (product.getExpiryDate() < todayTimestamp) {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expired_stroke));
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expired_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_highlighted)); // Definisci questa dimen
                        // cardProductItem.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expired_background)); // Sfondo opzionale
                    }
                }
                // Stato 2: In scadenza a breve
                else if (product.getExpiryDate() <= warningTimestamp) {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expiring_soon_stroke));
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expiring_soon_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_highlighted));
                        // cardProductItem.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expiring_soon_background)); // Sfondo opzionale
                    }
                }
                // Stato 3: Non in scadenza a breve / Non scaduto
                else {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_text)); // O il tuo colore di default
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_default)); // Definisci questa dimen
                        // cardProductItem.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_background));
                    }
                }
            } else {
                textViewExpiryDate.setText("Scad: N/D");
                textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_text)); // O il tuo colore di default
                if (cardProductItem != null) {
                    cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_stroke));
                    cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_default));
                    // cardProductItem.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_background));
                }
            }        
        
        }
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