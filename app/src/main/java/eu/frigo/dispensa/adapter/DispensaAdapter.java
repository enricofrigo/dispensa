package eu.frigo.dispensa.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;

public class DispensaAdapter extends ListAdapter<Dispensa, DispensaAdapter.DispensaViewHolder> {

    private final OnDispensaClickListener listener;
    private int currentDispensaId = -1;
    private final java.util.Map<Integer, JoinedPantryConfig> joinedConfigsMap = new java.util.HashMap<>();

    public interface OnDispensaClickListener {
        void onDispensaClick(Dispensa dispensa);
        void onEditClick(Dispensa dispensa);
        void onDevicesClick(Dispensa dispensa);
        void onShareClick(Dispensa dispensa);
        void onDeleteClick(Dispensa dispensa);
        void onSetDefaultClick(Dispensa dispensa);
    }

    public DispensaAdapter(OnDispensaClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setCurrentDispensaId(int currentDispensaId) {
        this.currentDispensaId = currentDispensaId;
        notifyDataSetChanged();
    }

    public void setJoinedConfigs(List<JoinedPantryConfig> configs) {
        joinedConfigsMap.clear();
        if (configs != null) {
            for (JoinedPantryConfig c : configs) {
                joinedConfigsMap.put(c.dispensaId, c);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DispensaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dispensa, parent, false);
        return new DispensaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DispensaViewHolder holder, int position) {
        Dispensa dispensa = getItem(position);
        JoinedPantryConfig config = joinedConfigsMap.get(dispensa.id);
        holder.bind(dispensa, config, listener, currentDispensaId);
    }

    static class DispensaViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewName;
        private final TextView textViewOwnerName;
        private final android.widget.ImageView imageViewProviderIcon;
        private final ImageButton buttonDefault;
        private final ImageButton buttonShare;
        private final ImageButton buttonDevices;
        private final ImageButton buttonEdit;
        private final ImageButton buttonDelete;

        public DispensaViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewDispensaName);
            textViewOwnerName = itemView.findViewById(R.id.textViewOwnerName);
            imageViewProviderIcon = itemView.findViewById(R.id.imageViewProviderIcon);
            buttonDefault = itemView.findViewById(R.id.buttonDefault);
            buttonShare = itemView.findViewById(R.id.buttonShare);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
            buttonDevices = itemView.findViewById(R.id.buttonDevices);
        }

        public void bind(Dispensa dispensa, JoinedPantryConfig config, OnDispensaClickListener listener, int currentDispensaId) {
            textViewName.setText(dispensa.getName());
            
            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(itemView.getContext());
            boolean isOwner = dispensa.deviceOwnerId == null || dispensa.deviceOwnerId.equals(currentDeviceId);

            if (!isOwner && dispensa.deviceOwnerName != null) {
                textViewOwnerName.setVisibility(View.VISIBLE);
                textViewOwnerName.setText(itemView.getContext().getString(R.string.pantry_owner_format, dispensa.deviceOwnerName));
                
                // Hide owner-only actions
                buttonShare.setVisibility(View.GONE);
                buttonDevices.setVisibility(View.GONE);
                buttonEdit.setVisibility(View.GONE);
            } else {
                textViewOwnerName.setVisibility(View.GONE);
                
                buttonShare.setVisibility(View.VISIBLE);
                buttonDevices.setVisibility(View.VISIBLE);
                buttonEdit.setVisibility(View.VISIBLE);
            }

            // Provider Icon & Badge
            if (config != null) {
                eu.frigo.dispensa.sync.sharing.SharingProvider provider = 
                        eu.frigo.dispensa.sync.sharing.PantrySharingService.getInstance().getProvider(config.providerId);
                if (provider != null) {
                    imageViewProviderIcon.setImageResource(provider.getIconResId());
                } else {
                    imageViewProviderIcon.setImageResource(R.drawable.ic_provider_webdav);
                }
                imageViewProviderIcon.setVisibility(View.VISIBLE);
            } else {
                imageViewProviderIcon.setVisibility(View.GONE);
            }

            if (dispensa.id == currentDispensaId) {
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.purple_200));
            } else {
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.transparent));
            }

            android.util.TypedValue typedValue = new android.util.TypedValue();
            itemView.getContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, typedValue, true);
            int colorSelected = (typedValue.resourceId != 0)
                    ? ContextCompat.getColor(itemView.getContext(), typedValue.resourceId)
                    : typedValue.data;

            if (dispensa.isDefault()) {
                buttonDefault.setImageTintList(android.content.res.ColorStateList.valueOf(colorSelected));
            } else {
                buttonDefault.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            }

            itemView.setOnClickListener(v -> listener.onDispensaClick(dispensa));
            buttonDefault.setOnClickListener(v -> listener.onSetDefaultClick(dispensa));
            buttonShare.setOnClickListener(v -> listener.onShareClick(dispensa));
            buttonEdit.setOnClickListener(v -> listener.onEditClick(dispensa));
            buttonDelete.setOnClickListener(v -> listener.onDeleteClick(dispensa));
            buttonDevices.setOnClickListener(v -> listener.onDevicesClick(dispensa));
        }
    }

    private static final DiffUtil.ItemCallback<Dispensa> DIFF_CALLBACK = new DiffUtil.ItemCallback<Dispensa>() {
        @Override
        public boolean areItemsTheSame(@NonNull Dispensa oldItem, @NonNull Dispensa newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Dispensa oldItem, @NonNull Dispensa newItem) {
            return oldItem.getName().equals(newItem.getName()) && 
                    oldItem.isDefault() == newItem.isDefault() &&
                    oldItem.lastModified == newItem.lastModified;
        }
    };
}
