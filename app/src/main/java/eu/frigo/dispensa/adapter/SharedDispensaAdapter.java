package eu.frigo.dispensa.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.sharing.SharingProvider;

public class SharedDispensaAdapter extends RecyclerView.Adapter<SharedDispensaAdapter.ViewHolder> {

    public interface OnSharedDispensaActionListener {
        void onDevicesClick(Dispensa dispensa);
        void onShareQrClick(Dispensa dispensa);
        void onProviderClick(SharingProvider provider, JoinedPantryConfig config);
        void onUnshareClick(Dispensa dispensa, JoinedPantryConfig config);
    }

    public static class SharedPantryItem {
        public final Dispensa dispensa;
        public final JoinedPantryConfig config;
        public final SharingProvider provider;

        public SharedPantryItem(Dispensa dispensa, JoinedPantryConfig config, SharingProvider provider) {
            this.dispensa = dispensa;
            this.config = config;
            this.provider = provider;
        }
    }

    private final List<SharedPantryItem> items = new ArrayList<>();
    private final OnSharedDispensaActionListener listener;

    public SharedDispensaAdapter(OnSharedDispensaActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SharedPantryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_dispensa, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPantryName;
        private final TextView tvProviderName;
        private final ImageView ivProviderIcon;
        private final View badgeProvider;
        private final TextView tvPantryDetails;
        private final TextView tvPantryOwner;
        private final ImageButton btnDevices;
        private final ImageButton btnShareQr;
        private final ImageButton btnUnshare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPantryName = itemView.findViewById(R.id.tv_shared_pantry_name);
            tvProviderName = itemView.findViewById(R.id.tv_shared_provider_name);
            ivProviderIcon = itemView.findViewById(R.id.iv_shared_provider_icon);
            badgeProvider = itemView.findViewById(R.id.badge_provider);
            tvPantryDetails = itemView.findViewById(R.id.tv_shared_pantry_details);
            tvPantryOwner = itemView.findViewById(R.id.tv_shared_pantry_owner);
            btnDevices = itemView.findViewById(R.id.btn_shared_devices);
            btnShareQr = itemView.findViewById(R.id.btn_shared_qr);
            btnUnshare = itemView.findViewById(R.id.btn_shared_unshare);
        }

        public void bind(SharedPantryItem item, OnSharedDispensaActionListener listener) {
            Context context = itemView.getContext();
            Dispensa dispensa = item.dispensa;
            JoinedPantryConfig config = item.config;
            SharingProvider provider = item.provider;

            tvPantryName.setText(dispensa.getName());

            if (provider != null) {
                tvProviderName.setText(provider.getDisplayName(context));
                ivProviderIcon.setImageResource(provider.getIconResId());
            } else if (config != null) {
                tvProviderName.setText(config.providerId != null ? config.providerId : "Cloud");
                ivProviderIcon.setImageResource(R.drawable.ic_provider_webdav);
            } else {
                tvProviderName.setText("WebDAV");
                ivProviderIcon.setImageResource(R.drawable.ic_provider_webdav);
            }

            // Path or destination details
            if (config != null && config.url != null && !config.url.trim().isEmpty()) {
                tvPantryDetails.setText(config.url);
                tvPantryDetails.setVisibility(View.VISIBLE);
            } else if (provider != null) {
                tvPantryDetails.setText(provider.getSummary(context));
                tvPantryDetails.setVisibility(View.VISIBLE);
            } else {
                tvPantryDetails.setVisibility(View.GONE);
            }

            // Owner info
            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            boolean isOwner = dispensa.deviceOwnerId == null || dispensa.deviceOwnerId.equals(currentDeviceId);
            if (isOwner) {
                tvPantryOwner.setText(R.string.pantry_status_owner);
                btnShareQr.setVisibility(View.VISIBLE);
                btnDevices.setVisibility(View.VISIBLE);
            } else {
                String ownerName = dispensa.deviceOwnerName != null ? dispensa.deviceOwnerName : "Altro dispositivo";
                tvPantryOwner.setText(context.getString(R.string.pantry_status_guest, ownerName));
                btnShareQr.setVisibility(View.GONE);
                btnDevices.setVisibility(View.GONE);
            }

            badgeProvider.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProviderClick(provider, config);
                }
            });

            btnDevices.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDevicesClick(dispensa);
                }
            });

            btnShareQr.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareQrClick(dispensa);
                }
            });

            btnUnshare.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUnshareClick(dispensa, config);
                }
            });
        }
    }
}
