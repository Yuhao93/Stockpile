package stockpile.haodev.org.stockpile;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class StockConfigurationActivity extends AppCompatActivity implements View.OnClickListener {
  private LinearLayout container;
  private TrackedItems items;
  private StockServiceConnection connection = new StockServiceConnection();
  private StockCheckerService.StockCheckerServiceBinder binder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.stock_configuration);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.setOnClickListener(this);
    container = (LinearLayout) findViewById(R.id.tracked_items_container);
    items = new TrackedItems(this);
    updatedItems();

    for (TrackedItems.Item item : items.items()) {
      container.addView(createViewForItem(item));
    }
    bindService(new Intent(this, StockCheckerService.class), connection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unbindService(connection);
  }

  @Override
  public void onClick(View view) {
    new AlertDialog.Builder(this)
        .setTitle("Track new item")
        .setView(getLayoutInflater().inflate(R.layout.new_item, null))
        .setPositiveButton("Add", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            AlertDialog dialog = (AlertDialog) dialogInterface;
            TrackedItems.Item item = new TrackedItems.Item();
            item.id = ((TextView) dialog.findViewById(R.id.id_input)).getText().toString();
            item.name = ((TextView) dialog.findViewById(R.id.item_input)).getText().toString();
            item.active = true;
            items.addItem(item);
            container.addView(createViewForItem(item));
            updatedItems();
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {}
        })
        .create()
        .show();
  }

  public void invalidateContainer() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        items = new TrackedItems(StockConfigurationActivity.this);
        container.removeAllViews();
        for (TrackedItems.Item item : items.items()) {
          container.addView(createViewForItem(item));
        }
      }
    });
  }

  private void updatedItems() {
    startService(new Intent(this, StockCheckerService.class));
    Toast.makeText(this, "Service updated", Toast.LENGTH_LONG).show();
  }

  private View createViewForItem(final TrackedItems.Item item) {
    View row = getLayoutInflater().inflate(R.layout.tracked_item, container, false);
    ((TextView) row.findViewById(R.id.text)).setText(String.format("%s - %s", item.id, item.name));
    row.findViewById(R.id.close).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        items.remove(item.id);
        updatedItems();
        ((ViewGroup) view.getParent().getParent()).removeView((View) view.getParent());
      }
    });
    final ImageView alarmButton = (ImageView) row.findViewById(R.id.alarm);
    alarmButton.setImageResource(
        item.active ? R.drawable.ic_alarm_on_black_48dp : R.drawable.ic_alarm_off_black_48dp);
    row.findViewById(R.id.alarm).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        items.toggleActive(item.id);
        updatedItems();
        alarmButton.setImageResource(
            item.active ? R.drawable.ic_alarm_on_black_48dp : R.drawable.ic_alarm_off_black_48dp);
      }
    });
    return row;
  }

  private class StockServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      binder = (StockCheckerService.StockCheckerServiceBinder) service;
      binder.bindActivity(StockConfigurationActivity.this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      binder.unbindActivity();
    }
  }
}
