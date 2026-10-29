package eu.frigo.dispensa.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.slider.Slider;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.product.Product;

public class ConsumeBottomSheetHelper {

    public interface OnConsumeActionListener {
        void onConsumeWholeUnit(Product product);
        void onUpdatePercentage(Product product, int newPercentage);
        void onSplitAndConsume(Product originalProduct, int newPercentage);
    }

    public static void show(Context context, ProductWithCategoryDefinitions productWithDefs, OnConsumeActionListener listener) {
        if (context == null || productWithDefs == null || productWithDefs.product == null) return;
        Product product = productWithDefs.product;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_consume_product, null);
        dialog.setContentView(view);

        TextView textViewName = view.findViewById(R.id.textViewConsumeProductName);
        TextView textViewSubtitle = view.findViewById(R.id.textViewConsumeProductSubtitle);
        MaterialButton buttonConsumeWhole = view.findViewById(R.id.buttonConsumeWholeUnit);
        MaterialDivider dividerWhole = view.findViewById(R.id.dividerWholeUnit);
        TextView textViewPercentageDisplay = view.findViewById(R.id.textViewPercentageDisplay);
        Slider slider = view.findViewById(R.id.sliderRemainingPercentage);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupPercentages);
        Chip chip100 = view.findViewById(R.id.chipPercent100);
        Chip chip75 = view.findViewById(R.id.chipPercent75);
        Chip chip50 = view.findViewById(R.id.chipPercent50);
        Chip chip25 = view.findViewById(R.id.chipPercent25);
        Chip chip0 = view.findViewById(R.id.chipPercent0);
        TextView textViewSplitInfo = view.findViewById(R.id.textViewSplitInfo);
        MaterialButton buttonConfirm = view.findViewById(R.id.buttonConfirmConsume);

        // Nome
        String name = product.getProductName();
        if (name == null || name.trim().isEmpty()) {
            name = product.getBarcode();
        }
        textViewName.setText(name);

        // Subtitle
        String expiryStr = product.getExpiryDateString();
        String locationStr = product.getStorageLocation() != null ? product.getStorageLocation() : "";
        int qty = product.getQuantity();
        String subtitle = locationStr + " • " + context.getString(R.string.expiry_date_label, expiryStr) + " • " + context.getString(R.string.quantity_label, qty);
        textViewSubtitle.setText(subtitle);

        // Sezione consuma unità intera
        if (qty > 1) {
            buttonConsumeWhole.setVisibility(View.VISIBLE);
            dividerWhole.setVisibility(View.VISIBLE);
            buttonConsumeWhole.setText(context.getString(R.string.consume_whole_unit, qty - 1));
            buttonConsumeWhole.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    listener.onConsumeWholeUnit(product);
                }
            });
        } else {
            buttonConsumeWhole.setVisibility(View.GONE);
            dividerWhole.setVisibility(View.GONE);
        }

        int initialPercentage = product.getRemainingPercentage();
        final int[] currentPercentage = { initialPercentage };

        Runnable updateDisplay = () -> {
            int p = currentPercentage[0];
            textViewPercentageDisplay.setText(p + "%");
            int color;
            if (p > 50) {
                color = ContextCompat.getColor(context, R.color.md_theme_primary);
            } else if (p > 25) {
                color = ContextCompat.getColor(context, R.color.product_expiring_soon_stroke);
            } else {
                color = ContextCompat.getColor(context, R.color.product_expired_stroke);
            }
            textViewPercentageDisplay.setTextColor(color);

            // Avviso scorporo
            if (qty > 1 && p < 100 && p > 0) {
                textViewSplitInfo.setVisibility(View.VISIBLE);
                textViewSplitInfo.setText(context.getString(R.string.consume_split_info, p, qty - 1));
            } else {
                textViewSplitInfo.setVisibility(View.GONE);
            }
        };

        // Slider listener
        slider.setValue((float) initialPercentage);
        slider.addOnChangeListener((s, value, fromUser) -> {
            int rounded = Math.round(value);
            currentPercentage[0] = rounded;
            updateDisplay.run();

            // Sincronizza chips
            if (rounded == 100) chipGroup.check(R.id.chipPercent100);
            else if (rounded == 75) chipGroup.check(R.id.chipPercent75);
            else if (rounded == 50) chipGroup.check(R.id.chipPercent50);
            else if (rounded == 25) chipGroup.check(R.id.chipPercent25);
            else if (rounded == 0) chipGroup.check(R.id.chipPercent0);
            else chipGroup.clearCheck();
        });

        // Chips listener
        chip100.setOnClickListener(v -> slider.setValue(100f));
        chip75.setOnClickListener(v -> slider.setValue(75f));
        chip50.setOnClickListener(v -> slider.setValue(50f));
        chip25.setOnClickListener(v -> slider.setValue(25f));
        chip0.setOnClickListener(v -> slider.setValue(0f));

        if (initialPercentage == 100) chipGroup.check(R.id.chipPercent100);
        else if (initialPercentage == 75) chipGroup.check(R.id.chipPercent75);
        else if (initialPercentage == 50) chipGroup.check(R.id.chipPercent50);
        else if (initialPercentage == 25) chipGroup.check(R.id.chipPercent25);
        else if (initialPercentage == 0) chipGroup.check(R.id.chipPercent0);

        updateDisplay.run();

        // Pulsante conferma
        buttonConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            int selectedP = currentPercentage[0];
            if (listener == null) return;

            if (selectedP == 0) {
                listener.onConsumeWholeUnit(product);
            } else if (qty > 1 && selectedP < 100) {
                listener.onSplitAndConsume(product, selectedP);
            } else {
                listener.onUpdatePercentage(product, selectedP);
            }
        });

        dialog.show();
    }
}
