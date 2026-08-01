package eu.frigo.dispensa.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.DispensaAdapter;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;

public class DispensaManagerActivity extends AppCompatActivity implements DispensaAdapter.OnDispensaClickListener {

    private DispensaViewModel dispensaViewModel;
    private DispensaAdapter adapter;

    private final ActivityResultLauncher<Intent> joinLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Se il join ha avuto successo, chiudiamo questa attività per mostrare la nuova dispensa
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dispensa_manager);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.manage_dispense_title);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerViewDispense);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DispensaAdapter(this);
        recyclerView.setAdapter(adapter);

        dispensaViewModel = new ViewModelProvider(this).get(DispensaViewModel.class);
        dispensaViewModel.getAllDispense().observe(this, dispense -> {
            adapter.submitList(dispense);
        });

        dispensaViewModel.getCurrentDispensaId().observe(this, id -> {
            adapter.setCurrentDispensaId(id != null ? id : -1);
        });

        dispensaViewModel.getPantryCreatedEvent().observe(this, created -> {
            if (created != null && created) {
                finish();
            }
        });

        FloatingActionButton fab = findViewById(R.id.fabAddDispensa);
        fab.setOnClickListener(v -> showAddEditDialog(null));

        FloatingActionButton fjb = findViewById(R.id.fabJoinDispensa);
        fjb.setOnClickListener(v ->{
            Intent intent = new Intent(this, SyncOnboardingActivity.class);
            intent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_JOIN);
            joinLauncher.launch(intent);
        });
    }

    private void showAddEditDialog(Dispensa dispensa) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(dispensa == null ? R.string.add_dispensa : R.string.edit_dispensa);

        final EditText input = new EditText(this);
        input.setPadding(40, 40, 40, 40);
        if (dispensa != null) {
            input.setText(dispensa.getName());
        }
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                if (dispensa == null) {
                    Dispensa newDispensa = new Dispensa(name, false);
                    dispensaViewModel.insert(newDispensa, true);
                } else {
                    dispensa.setName(name);
                    dispensaViewModel.update(dispensa);
                }
            } else {
                Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    @Override
    public void onDispensaClick(Dispensa dispensa) {
        dispensaViewModel.setCurrentDispensaId(dispensa.id);
        finish();
    }

    @Override
    public void onEditClick(Dispensa dispensa) {
        showAddEditDialog(dispensa);
    }

    @Override
    public void onShareClick(Dispensa dispensa) {
        Intent intent = new Intent(this, SyncOnboardingActivity.class);
        intent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_SHARE);
        startActivity(intent);
    }

    @Override
    public void onDevicesClick(Dispensa dispensa) {
        Intent intent = new Intent(this, ManageDevicesActivity.class);
        intent.putExtra(ManageDevicesActivity.PANTRY_ID, dispensa);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(Dispensa dispensa) {
        if (dispensa.isDefault()) {
            Toast.makeText(this, R.string.cannot_delete_default_dispensa, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_dispensa_title)
                .setMessage(R.string.delete_dispensa_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    dispensaViewModel.delete(dispensa);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onSetDefaultClick(Dispensa dispensa) {
        dispensaViewModel.setAsDefault(dispensa.id);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
