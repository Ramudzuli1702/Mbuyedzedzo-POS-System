package com.pos.scanner;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.*;
import java.util.*;

/** Staff sign-in -> immutable inventory snapshot -> local physical counts -> desktop approval. */
public final class StocktakeActivity extends AuthenticatedActivity {
    private WiFiCommunication wifi;
    private final Gson gson=new Gson();
    private StocktakeDraft draft;
    private SharedPreferences drafts;
    private String draftKey;
    private boolean busy,binding;
    private TextView status,summary,empty;
    private TextInputEditText search,notes;
    private View countPanel,progress;
    private Button submit,scan,newCount;
    private final Items adapter=new Items();
    private final BroadcastReceiver events=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent event){
            if(WiFiCommunication.ACTION_STOCKTAKE_STATUS.equals(event.getAction())&&draft!=null){
                try{JsonObject message=JsonParser.parseString(event.getStringExtra("message")).getAsJsonObject();
                    if(draft.id.equals(message.get("stocktakeId").getAsString())){draft.status=message.get("status").getAsString();draft.reviewNotes=message.get("reviewNotes").getAsString();persist();render();}
                }catch(RuntimeException ignored){}
            }else{render();if(!wifi.isConnected())message("Offline. Counts are saved on this phone. Reconnect and sign in to submit.",false);}
        }
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_stocktake);
        wifi=WiFiCommunication.getInstance(this);drafts=getSharedPreferences("stocktake_drafts",MODE_PRIVATE);
        status=findViewById(R.id.stocktakeStatus);summary=findViewById(R.id.stocktakeSummary);empty=findViewById(R.id.stocktakeEmpty);
        search=findViewById(R.id.stocktakeSearch);notes=findViewById(R.id.stocktakeNotes);
        countPanel=findViewById(R.id.stocktakeCountPanel);progress=findViewById(R.id.stocktakeProgress);
        submit=findViewById(R.id.stocktakeSubmit);scan=findViewById(R.id.stocktakeScan);newCount=findViewById(R.id.stocktakeNew);
        RecyclerView list=findViewById(R.id.stocktakeItems);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);
        findViewById(R.id.stocktakeBack).setOnClickListener(v->finish());
        submit.setOnClickListener(v->confirmSubmit());scan.setOnClickListener(v->scan());newCount.setOnClickListener(v->fresh());
        search.addTextChangedListener(watcher(()->adapter.filter(search.getText().toString())));
        notes.addTextChangedListener(watcher(()->{if(!binding&&draft!=null&&"Draft".equals(draft.status)){draft.notes=notes.getText().toString();persist();}}));
        IntentFilter filter=new IntentFilter("WIFI_CONNECTED");filter.addAction(WiFiCommunication.ACTION_STOCKTAKE_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(events,filter);
        if(wifi.isSignedIn())load(false);
    }
    private TextWatcher watcher(Runnable action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable s){action.run();}};}
    private void load(boolean fresh){
        if(!wifi.isSignedIn()){requireLogin();return;}
        draftKey="draft:"+wifi.getServerAddress()+":"+wifi.getStaffId();
        StocktakeDraft local=null;
        if(!fresh)try{local=gson.fromJson(drafts.getString(draftKey,null),StocktakeDraft.class);}catch(RuntimeException ignored){}
        final StocktakeDraft saved=local;
        JsonObject request=new JsonObject();request.addProperty("type",saved==null?"stocktake_start":"stocktake_resume");
        request.addProperty("stocktakeId",saved==null?UUID.randomUUID().toString():saved.id);
        // Store the ID before sending so a lost reply can resume the same server snapshot.
        if(saved==null){StocktakeDraft pending=new StocktakeDraft();pending.id=request.get("stocktakeId").getAsString();pending.staffId=wifi.getStaffId();draft=pending;persist();}
        else draft=saved;
        setBusy(true);message("Loading inventory from the desktop…",false);
        wifi.request(request,response->{
            if(isDestroyed())return;setBusy(false);
            if(!ok(response)){
                if(response.has("code")&&"NOT_FOUND".equals(response.get("code").getAsString())&&draft.counted()==0){load(true);return;}
                failure(response);return;
            }
            draft=gson.fromJson(response.get("data"),StocktakeDraft.class);draft.restoreLocalCounts(saved);persist();render();
        });
    }
    private boolean ok(JsonObject response){return response.has("ok")&&response.get("ok").getAsBoolean();}
    private void failure(JsonObject response){
        if(response.has("code")&&"AUTH_REQUIRED".equals(response.get("code").getAsString())){requireLogin();return;}
        render();message(response.has("message")?response.get("message").getAsString():"Please retry.",true);
    }
    private boolean editable(){return draft!=null&&"Draft".equals(draft.status)&&!busy;}
    private void render(){
        countPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        if(draft==null)return;
        summary.setText(getString(R.string.count_progress,draft.counted(),draft.items.size(),draft.status));
        binding=true;if(!notes.getText().toString().equals(draft.notes))notes.setText(draft.notes);binding=false;
        notes.setEnabled(editable());scan.setEnabled(editable());newCount.setEnabled(!busy&&wifi.isConnected()&&wifi.isSignedIn());
        submit.setText(wifi.isSignedIn()?R.string.submit_for_approval:R.string.sign_in_to_submit);
        submit.setEnabled(!busy&&"Draft".equals(draft.status)&&draft.counted()>0&&wifi.isConnected());
        adapter.filter(search.getText().toString());
        if(!busy){
            if("Pending".equals(draft.status))message(getString(R.string.stocktake_pending),false);
            else if("Approved".equals(draft.status))message(getString(R.string.stocktake_approved)+reviewNotes(),false);
            else if("Rejected".equals(draft.status))message(getString(R.string.stocktake_rejected)+reviewNotes(),true);
            else message("Counts are saved on this phone. Uncounted products stay unchanged until you count them.",false);
        }
    }
    private String reviewNotes(){return draft.reviewNotes==null||draft.reviewNotes.trim().isEmpty()?"":"\nReview notes: "+draft.reviewNotes;}
    private void message(String text,boolean error){status.setText(text);status.setTextColor(ContextCompat.getColor(this,error?R.color.status_error:R.color.brand_primary_dark));}
    private void setBusy(boolean value){busy=value;render();}
    private void persist(){if(draft!=null&&draftKey!=null&&!drafts.edit().putString(draftKey,gson.toJson(draft)).commit())message("The phone could not save this draft. Free some storage before leaving this screen.",true);}
    private void count(StocktakeDraft.Item item){
        if(!editable())return;
        TextInputLayout wrapper=new TextInputLayout(this);wrapper.setHint(getString(R.string.physical_quantity));
        int padding=(int)(20*getResources().getDisplayMetrics().density);wrapper.setPadding(padding,0,padding,0);
        TextInputEditText input=new TextInputEditText(wrapper.getContext());input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);wrapper.addView(input);
        if(item.counted!=null){input.setText(String.valueOf(item.counted));input.selectAll();}
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(item.name).setMessage("Loaded stock: "+item.expected+"\nEnter the physical quantity, including zero when none remain.")
                .setView(wrapper).setPositiveButton(R.string.save_count,null).setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.clear_count,(d,w)->{item.counted=null;persist();render();}).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
            Integer quantity=StocktakeDraft.parseCount(input.getText().toString());
            if(quantity==null){wrapper.setError(getString(R.string.count_validation));return;}
            item.counted=quantity;persist();render();dialog.dismiss();
        }));dialog.show();
    }
    private void scan(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},401);return;}
        Intent intent=new Intent(this,ScannerActivity.class);intent.putExtra("mode","stocktake");startActivityForResult(intent,402);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==401&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)scan();}
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);if(code!=402||result!=RESULT_OK||data==null||draft==null)return;
        String barcode=data.getStringExtra("barcode");List<StocktakeDraft.Item> matches=new ArrayList<>();
        for(StocktakeDraft.Item item:draft.items)if(Objects.equals(barcode,item.barcode))matches.add(item);
        if(matches.isEmpty())for(StocktakeDraft.Item item:draft.items)if(ScanFormats.sameProduct(barcode,item.barcode))matches.add(item);
        if(matches.size()==1)count(matches.get(0));else message(matches.isEmpty()?"Barcode not found in this inventory. Search by product name.":"More than one product uses this barcode. Select the product by name.",true);
    }
    private void confirmSubmit(){
        if(!wifi.isSignedIn()){requireLogin();return;}
        if(draft==null||busy||draft.counted()==0)return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.submit_for_approval)
                .setMessage(getString(R.string.submit_counted_products,draft.counted(),draft.items.size()-draft.counted()))
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.submit_for_approval,(d,w)->{
                    JsonObject request=new JsonObject();request.addProperty("type","stocktake_submit");request.addProperty("stocktakeId",draft.id);request.addProperty("notes",draft.notes);
                    JsonArray counts=new JsonArray();for(StocktakeDraft.Item item:draft.items)if(item.counted!=null){JsonObject row=new JsonObject();row.addProperty("productId",item.productId);row.addProperty("quantity",item.counted);counts.add(row);}request.add("counts",counts);
                    setBusy(true);message("Sending counts for approval…",false);
                    wifi.request(request,response->{if(isDestroyed())return;setBusy(false);if(ok(response)){draft=gson.fromJson(response.get("data"),StocktakeDraft.class);persist();render();}else failure(response);});
                }).show();
    }
    private void fresh(){new MaterialAlertDialogBuilder(this).setTitle(R.string.start_fresh_count)
            .setMessage("Load current inventory and start again? Unsubmitted counts on this phone will be replaced. Previously submitted stocktakes remain on the desktop.")
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.start_fresh_count,(d,w)->load(true)).show();}
    @Override protected void onDestroy(){persist();LocalBroadcastManager.getInstance(this).unregisterReceiver(events);super.onDestroy();}

    private final class Items extends RecyclerView.Adapter<Holder>{
        private final List<StocktakeDraft.Item> visible=new ArrayList<>();
        void filter(String text){visible.clear();if(draft!=null)for(StocktakeDraft.Item item:draft.items){String value=(item.name+" "+item.barcode+" "+item.category).toLowerCase(Locale.ROOT);if(value.contains(text.toLowerCase(Locale.ROOT)))visible.add(item);}notifyDataSetChanged();empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stocktake,parent,false));}
        @Override public void onBindViewHolder(Holder holder,int position){StocktakeDraft.Item item=visible.get(position);holder.name.setText(item.name);holder.barcode.setText(item.barcode==null?"No barcode":item.barcode);holder.expected.setText(getString(R.string.loaded_stock,item.expected));holder.count.setText(item.counted==null?getString(R.string.count):String.valueOf(item.counted));holder.count.setEnabled(editable());holder.count.setOnClickListener(v->count(item));}
        @Override public int getItemCount(){return visible.size();}
    }
    private static final class Holder extends RecyclerView.ViewHolder{
        TextView name,barcode,expected;Button count;
        Holder(View view){super(view);name=view.findViewById(R.id.countProductName);barcode=view.findViewById(R.id.countBarcode);expected=view.findViewById(R.id.countExpected);count=view.findViewById(R.id.countPhysical);}
    }
}
