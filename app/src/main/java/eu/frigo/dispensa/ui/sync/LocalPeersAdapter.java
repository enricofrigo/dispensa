package eu.frigo.dispensa.ui.sync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.frigo.dispensa.R;

public class LocalPeersAdapter extends RecyclerView.Adapter<LocalPeersAdapter.PeerViewHolder> {
    
    private final List<LocalNetworkConfigFragment.PeerDevice> peers;
    private final OnPeerClickListener listener;
    
    public interface OnPeerClickListener {
        void onPeerClick(LocalNetworkConfigFragment.PeerDevice peer);
    }
    
    public LocalPeersAdapter(List<LocalNetworkConfigFragment.PeerDevice> peers, 
                             OnPeerClickListener listener) {
        this.peers = peers;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer_device, parent, false);
        return new PeerViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        LocalNetworkConfigFragment.PeerDevice peer = peers.get(position);
        holder.bind(peer, listener);
    }
    
    @Override
    public int getItemCount() {
        return peers.size();
    }
    
    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDeviceName;
        private final TextView textDeviceId;
        private final ImageButton btnTrust;
        
        public PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            textDeviceName = itemView.findViewById(R.id.text_peer_name);
            textDeviceId = itemView.findViewById(R.id.text_peer_id);
            btnTrust = itemView.findViewById(R.id.btn_trust_peer);
        }
        
        public void bind(LocalNetworkConfigFragment.PeerDevice peer, 
                       OnPeerClickListener listener) {
            textDeviceName.setText(peer.friendlyName);
            textDeviceId.setText(peer.deviceId);
            
            int trustIcon = peer.trusted ? R.drawable.ic_check_circle : R.drawable.ic_circle;
            btnTrust.setImageResource(trustIcon);
            
            btnTrust.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPeerClick(peer);
                }
            });
        }
    }
}
