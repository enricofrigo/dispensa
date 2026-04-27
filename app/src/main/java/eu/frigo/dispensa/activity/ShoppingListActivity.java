package eu.frigo.dispensa.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.ShoppingListAdapter;
import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.viewmodel.ShoppingListViewModel;

public class ShoppingListActivity extends AppCompatActivity
        implements ShoppingListAdapter.OnShoppingItemInteractionListener {

    private ShoppingListViewModel shoppingListViewModel;
    private ShoppingListAdapter adapter;
    private TextView textViewEmpty;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.applyLocaleOnCreate(this);
        setContentView(R.layout.activity_shopping_list);

        Toolbar toolbar = findViewById(R.id.toolbar_shopping_list);
        toolbar.setTitle(R.string.shopping_list_title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        RecyclerView recyclerView = findViewById(R.id.recyclerViewShoppingList);
        textViewEmpty = findViewById(R.id.textViewEmptyShoppingList);

        adapter = new ShoppingListAdapter(new ShoppingListAdapter.ShoppingItemDiff(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        shoppingListViewModel = new ViewModelProvider(this).get(ShoppingListViewModel.class);
        shoppingListViewModel.getAllItems().observe(this, items -> {
            if (items != null && !items.isEmpty()) {
                adapter.submitList(items);
                textViewEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                adapter.submitList(null);
                textViewEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_shopping_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_checked) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.shopping_list_clear_checked)
                    .setMessage(R.string.shopping_list_clear_confirm)
                    .setPositiveButton(R.string.ok, (dialog, which) ->
                            shoppingListViewModel.clearChecked())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemCheckedChanged(eu.frigo.dispensa.data.shoppinglist.ShoppingItem item) {
        shoppingListViewModel.toggleChecked(item);
    }
}
