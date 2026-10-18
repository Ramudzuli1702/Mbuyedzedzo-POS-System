package com.pos.scanner;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReceiptScreenTest {
    @Test public void receiptDetailsInflateWithTheirManifestTheme() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Context context = instrumentation.getTargetContext();
        int theme = context.getPackageManager().getActivityInfo(
                new ComponentName(context, ReceiptDetailActivity.class), 0).getThemeResource();
        instrumentation.runOnMainSync(() -> {
            ContextThemeWrapper themed = new ContextThemeWrapper(context, theme);
            View screen = LayoutInflater.from(themed).inflate(R.layout.activity_receipt_detail, null);
            assertNotNull(screen.findViewById(R.id.receiptBack));
            androidx.recyclerview.widget.RecyclerView items = (androidx.recyclerview.widget.RecyclerView) screen.findViewById(R.id.itemsRecyclerView);
            Receipt receipt = Receipt.fromString("Item   Qty   Amount\nBread\n  2 x 12.50 R25.00\nTOTAL R25.00");
            ReceiptItemAdapter adapter = new ReceiptItemAdapter(receipt.getItems());
            ReceiptItemAdapter.ItemViewHolder holder = adapter.onCreateViewHolder(items, 0);
            adapter.onBindViewHolder(holder, 0);
            assertEquals("Bread", holder.itemNameText.getText().toString());
        });
    }

    @Test public void stocktakeIconIsVisibleOnItsTealChip() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_stocktaking);
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(context.getColor(R.color.brand_primary));
        icon.setBounds(0,0,96,96); icon.draw(canvas);
        int white = 0;
        for(int x=0;x<96;x++)for(int y=0;y<96;y++)if(bitmap.getPixel(x,y)==Color.WHITE)white++;
        assertTrue("Clipboard strokes must contrast with the chip", white > 100);
    }
}
