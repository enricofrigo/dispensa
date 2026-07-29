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

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;

public class DispensaAdapter extends ListAdapter<Dispensa, DispensaAdapter.DispensaViewHolder> {

    private final OnDispensaClickListener listener;
    private int currentDispensaId = -1;

    public interface OnDispensaClickListener {
        void onDispensaClick(Dispensa dispensa);
        void onEditClick(Dispensa dispensa);
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

    @NonNull
    @Override
    public DispensaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dispensa, parent, false);
        return new DispensaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DispensaViewHolder holder, int position) {
        Dispensa dispensa = getItem(position);
        holder.bind(dispensa, listener, currentDispensaId);
    }

    static class DispensaViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewName;
        private final ImageButton buttonDefault;
        private final ImageButton buttonEdit;
        private final ImageButton buttonDelete;

        public DispensaViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewDispensaName);
            buttonDefault = itemView.findViewById(R.id.buttonDefault);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }

        public void bind(Dispensa dispensa, OnDispensaClickListener listener, int currentDispensaId) {
            textViewName.setText(dispensa.getName());
            
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
            buttonEdit.setOnClickListener(v -> listener.onEditClick(dispensa));
            buttonDelete.setOnClickListener(v -> listener.onDeleteClick(dispensa));
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
                    oldItem.isDefault() == newItem.isDefault();
        }
    };
}
