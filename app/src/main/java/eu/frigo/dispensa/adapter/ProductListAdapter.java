package eu.frigo.dispensa.adapter; // Crea un package adapter o simile

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;
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
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.ui.SettingsFragment;
import eu.frigo.dispensa.util.DateConverter;

public class ProductListAdapter extends ListAdapter<ProductWithCategoryDefinitions, ProductListAdapter.ProductViewHolder> {

    private ProductWithCategoryDefinitions selectedProduct;
    private final OnProductInteractionListener interactionListener;

    public interface OnProductInteractionListener {
        void onProductItemClickedForQuantity(ProductWithCategoryDefinitions product);
        void onEditActionClicked(ProductWithCategoryDefinitions product);
        void onDeleteActionClicked(ProductWithCategoryDefinitions product);
    }

    public ProductListAdapter(@NonNull DiffUtil.ItemCallback<ProductWithCategoryDefinitions> diffCallback, OnProductInteractionListener listener) {
        super(diffCallback);
        interactionListener=listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_product, parent, false);
        return new ProductViewHolder(itemView,interactionListener);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        ProductWithCategoryDefinitions currentProduct = getItem(position);
        holder.bind(currentProduct);
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener{
        private final TextView textViewQuantity;
        private final TextView textViewExpiryDate;
        private final TextView textViewProductName;
        private final ImageView imageViewProduct;
        private final MaterialCardView cardProductItem;
        private final OnProductInteractionListener listenerInternal;
        private ProductWithCategoryDefinitions currentProduct;
        private static final int SINGLE_CLICK_ACTION = 1;
        private static final int DOUBLE_CLICK_ACTION = 2;
        private static final long CLICK_TIMEOUT = 250;
        private final android.os.Handler clickHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        private int clickExecutionState = 0;

        ProductViewHolder(View itemView, OnProductInteractionListener listener) {
            super(itemView);
            this.listenerInternal = listener;
            textViewQuantity = itemView.findViewById(R.id.textViewItemQuantity);
            textViewExpiryDate = itemView.findViewById(R.id.textViewItemExpiryDate);
            textViewProductName = itemView.findViewById(R.id.textViewItemProductName);
            imageViewProduct = itemView.findViewById(R.id.imageViewItemProduct);
            cardProductItem = itemView.findViewById(R.id.card_product_item);
            itemView.setOnCreateContextMenuListener(this);
        }

        void bind(ProductWithCategoryDefinitions product) {
            this.currentProduct = product;
            if (product.product.getProductName() != null && !product.product.getProductName().isEmpty()) {
                textViewProductName.setText(product.product.getProductName());
                textViewProductName.setVisibility(View.VISIBLE);
            } else {
                textViewProductName.setText(product.product.getBarcode());
            }
            textViewQuantity.setText(itemView.getContext().getString(R.string.quantity_label, product.product.getQuantity()));

            textViewExpiryDate.setText(itemView.getContext().getString(R.string.expiry_date_label, product.product.getExpiryDateString()));
            if (product.product.getImageUrl() != null && !product.product.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(product.product.getImageUrl())
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_placeholder_image)
                        .into(imageViewProduct);
                imageViewProduct.setVisibility(View.VISIBLE);
            } else {
                imageViewProduct.setImageResource(R.drawable.ic_placeholder_image);
            }
            
            if (product.product.getExpiryDate() != null) {
                textViewExpiryDate.setText(String.format("Scad: %s", DateConverter.formatTimestampToDisplayDate(product.product.getExpiryDate())));
                textViewExpiryDate.setVisibility(View.VISIBLE);

                long todayTimestamp = DateConverter.getTodayNormalizedTimestamp(); // Mezzogiorno di oggi

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
                if (product.product.getExpiryDate() < todayTimestamp) {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expired_stroke));
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expired_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_highlighted)); // Definisci questa dimen
                    }
                }
                // Stato 2: In scadenza a breve
                else if (product.product.getExpiryDate() <= warningTimestamp) {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expiring_soon_stroke));
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_expiring_soon_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_highlighted));
                    }
                }
                // Stato 3: Non in scadenza a breve / Non scaduto
                else {
                    textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_text)); // O il tuo colore di default
                    if (cardProductItem != null) {
                        cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_stroke));
                        cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_default)); // Definisci questa dimen
                    }
                }
            } else {
                textViewExpiryDate.setText("Scad: N/D");
                textViewExpiryDate.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_text)); // O il tuo colore di default
                if (cardProductItem != null) {
                    cardProductItem.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.product_default_stroke));
                    cardProductItem.setStrokeWidth(itemView.getResources().getDimensionPixelSize(R.dimen.card_stroke_width_default));
                }
            }
            itemView.setOnClickListener(v -> {
                if (listenerInternal == null || currentProduct == null) {
                    return;
                }

                if (clickExecutionState == 0) { // Primo click o click dopo un timeout/azione
                    clickExecutionState = SINGLE_CLICK_ACTION; // Segna che un singolo click è in attesa
                    Log.d("ClickLogic", "First click. Single click action PENDING (Edit).");

                    // Posticipa l'azione del singolo click
                    clickHandler.postDelayed(() -> {
                        // Questo Runnable viene eseguito se non c'è un secondo click entro CLICK_TIMEOUT
                        if (clickExecutionState == SINGLE_CLICK_ACTION) { // Controlla se siamo ancora in attesa del singolo click
                            Log.d("ClickLogic", "Timeout expired. Executing SINGLE click action (Edit).");
                            listenerInternal.onEditActionClicked(currentProduct); // Esegui l'azione di modifica
                            clickExecutionState = 0; // Resetta lo stato
                        }
                    }, CLICK_TIMEOUT);

                } else if (clickExecutionState == SINGLE_CLICK_ACTION) { // Secondo click arrivato mentre un singolo click era in attesa
                    // Questo è un doppio click!
                    clickExecutionState = DOUBLE_CLICK_ACTION; // Segna che stiamo per eseguire un doppio click
                    Log.d("ClickLogic", "Second click detected. Executing DOUBLE click action (Decrement Quantity).");

                    // Rimuovi il Runnable del singolo click che era in attesa
                    clickHandler.removeCallbacksAndMessages(null); // Rimuove tutti i Runnable in sospeso per questo Handler

                    listenerInternal.onProductItemClickedForQuantity(currentProduct); // Esegui l'azione di decremento quantità
                    clickExecutionState = 0; // Resetta lo stato dopo l'azione
                }
            });        }
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            MenuItem edit = menu.add(Menu.NONE, R.id.action_edit_product, 1, "Modifica");
            MenuItem delete = menu.add(Menu.NONE, R.id.action_delete_product, 2, "Cancella");
            MenuItem usa = menu.add(Menu.NONE, R.id.action_delete_product, 2, "Usa");
            edit.setOnMenuItemClickListener(item -> {
                listenerInternal.onEditActionClicked(currentProduct);
                return true;
            });

            delete.setOnMenuItemClickListener(item -> {
                listenerInternal.onDeleteActionClicked(currentProduct);
                return true;
            });

            usa.setOnMenuItemClickListener(item -> {
                listenerInternal.onProductItemClickedForQuantity(currentProduct);
                return true;
            });
        }


    }

    public ProductWithCategoryDefinitions getProductAt(int position) {
        return getItem(position);
    }

    // Metodo per ottenere il prodotto selezionato (usato dall'Activity)
    public ProductWithCategoryDefinitions getSelectedProduct() {
        return selectedProduct;
    }

    // DiffUtil per aggiornamenti efficienti della lista
    public static class ProductDiff extends DiffUtil.ItemCallback<ProductWithCategoryDefinitions> {
        @Override
        public boolean areItemsTheSame(@NonNull ProductWithCategoryDefinitions oldItem, @NonNull ProductWithCategoryDefinitions newItem) {
            return oldItem.product.getId() == newItem.product.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ProductWithCategoryDefinitions oldItem, @NonNull ProductWithCategoryDefinitions newItem) {
            return oldItem.product.getBarcode().equals(newItem.product.getBarcode()) &&
                    oldItem.product.getQuantity() == newItem.product.getQuantity() &&
                    oldItem.product.getProductName().equals(newItem.product.getProductName()) &&
                    (oldItem.product.getImageUrl() == null || oldItem.product.getImageUrl().equals(newItem.product.getImageUrl())) &&
                    oldItem.product.getExpiryDate().equals(newItem.product.getExpiryDate());
        }
    }
}