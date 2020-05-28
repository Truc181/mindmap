package com.uit.mindmap.mapdrawer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.uit.mindmap.R;
import com.uit.mindmap.maploader.MapLoader;
import com.uit.mindmap.maploader.NodeData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapView extends RelativeLayout {
    Node[] nodes;
    List<Integer> selectedNodes;
    Paint paint;
    Path path;
    String mapName;
    NodeCustomizer nodeCustomizer;
    boolean changed=false;

    //region Constructor
    public MapView(Context context) {
        super(context);

        init(null);
    }

    public MapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init(attrs);
    }

    public MapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(attrs);
    }

    public void init(@Nullable AttributeSet set) {
        selectedNodes = new ArrayList<>();
        nodes = new Node[255];
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        path = new Path();
    }

    public void setMap(@Nullable Node[] nodes) {
        if (nodes == null) {
            addNode(null);
        } else {
            this.nodes = nodes;
            for (Node node : nodes) {
                if (node != null) {
                    addView(node);
                    node.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Node n = (Node) v;
                            selectNode(n.id);
                        }
                    });
                }
            }
        }
        selectNode(0);
    }
    //endregion

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int i = 0; i < 255; i++) {
            if (nodes[i] != null) {
                for (int a : nodes[i].children) {
                    drawLine(i, a, canvas);
                }
            }
        }
    }

    public void drawLine(int parent_node, int child_node, Canvas canvas) {
        paint.setColor(nodes[child_node].getConnectionColor());
        paint.setStrokeWidth(4);
        int[] p = nodes[parent_node].pos;
        int[] c = nodes[child_node].pos;
        path.reset();
        paint.setAntiAlias(true);
        float[] intervals;
        int style=nodes[child_node].getConnectionStyle();
        switch (style){
            case 0:
                paint.setPathEffect(null);
                break;
            case 1:
                intervals = new float[]{ 15, 15 };
                paint.setPathEffect(new DashPathEffect(intervals,0));
                break;
            case 2:
                intervals=  new float[]{ 15,15,5,15 };
                paint.setPathEffect(new DashPathEffect(intervals,0));
                break;
        }
        path.moveTo(p[0], p[1]);
        path.cubicTo(2 * c[0] / 3 + p[0] / 3, p[1], 2 * p[0] / 3 + c[0] / 3, c[1], c[0], c[1]);
        canvas.drawPath(path, paint);
    }

    //region Add
    public int addNode(@Nullable int[] pos) {
        Node node = new Node(getContext());
        addView(node);
        int i = 0;
        while (nodes[i] != null && i < 254) {
            i++;
        }
        nodes[i] = node;
        node.id = i;
        selectNode(i);
        if (pos != null) {
            node.setPosition(pos);
        } else {
            pos = new int[2];
            pos[0] += (int) getResources().getDimension(R.dimen.map_size) / 2;
            pos[1] += (int) getResources().getDimension(R.dimen.map_size) / 2;
            node.setPosition(pos);
        }
        node.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Node n = (Node) v;
                selectNode(n.id);
            }
        });
        return i;
    }

    public int addNode(int parent) {
        Node node = new Node(getContext());
        addView(node);
        node.parent = parent;
        int i = 0;
        while (nodes[i] != null && i < 254) {
            i++;
        }
        node.id = i;
        nodes[i] = node;
        selectNode(i);
        nodes[parent].addChild(i);
        int[] pos = new int[2];
        pos[0] = nodes[parent].pos[0];
        pos[1] = nodes[parent].pos[1];
        pos[0] += 50 + nodes[parent].getWidth();
        node.setPosition(pos);
        node.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Node n = (Node) v;
                selectNode(n.id);
            }
        });
        return i;
    }

    public void addNode() {
        for (int i : selectedNodes) {
            int node = addNode(i);
            nodes[node].setText("New node");
        }
        editText();
        changed=true;
    }
    //endregion

    //region Remove
    public void removeChildNode(int id) {
        for (int i : nodes[id].children) removeChildNode(i);
        removeView(nodes[id]);
        nodes[id] = null;
    }

    public void removeNode(int id) {
        if (id != 0) {
            for (int i : nodes[id].children) removeChildNode(i);
            nodes[nodes[id].parent].removeChildren(id);
            removeView(nodes[id]);
            nodes[id] = null;
        } else Toast.makeText(getContext(), "Cannot delete root node", Toast.LENGTH_SHORT).show();
    }

    public void removeNode(int[] ids) {
        for (int id : ids) {
            removeNode(id);
        }
    }

    public void removeNode() {
        for (int i : selectedNodes) {
            removeNode(i);
        }
        selectedNodes.clear();
        changed=true;
    }

    //endregion

    //region Selection
    public void deselect(int id) {
        nodes[id].defocus();
        selectedNodes.remove((Integer) id);
    }

    public void deselectAll() {
        for (int id : selectedNodes) {
            nodes[id].defocus();
        }
        selectedNodes.clear();
    }
    public void selectNode(int id) {
        deselectAll();
        selectedNodes.add(id);
        nodes[id].focus();
        nodes[id].bringToFront();
        View menu = ((MapDrawerActivity) getContext()).menu;
        BottomSheetBehavior bottomSheetBehavior= ((MapDrawerActivity) getContext()).bottomSheetBehavior;
        if (menu != null) menu.setVisibility(VISIBLE);
        if (bottomSheetBehavior!=null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }
    //endregion

    //region Customization
    public void applyTextSize(int text_size) {
        for (int i : selectedNodes) nodes[i].applyTextSize(text_size);
        changed=true;
    }

    public void applyTextColor(int color) {
        for (int i : selectedNodes) nodes[i].applyTextColor(color);
        changed=true;
    }

    public void applyBackgroundColor(int color) {
        for (int i : selectedNodes) nodes[i].applyBackgroundColor(color);
        changed=true;
    }

    public void applyOutlineColor(int color) {
        for (int i : selectedNodes) nodes[i].applyOutlineColor(color);
        changed=true;
    }
    public void applyConnectionStyle(int style){
        for (int i : selectedNodes) nodes[i].applyConnectionStyle(style);
        changed=true;
    }

    public void applyConnectionColor(int color) {
        for (int i : selectedNodes) nodes[i].applyConnectionColor(color);
        changed=true;
    }

    public void setNodeCustomizer(NodeCustomizer customizer){
        nodeCustomizer=customizer;
        customizer.setMapView(this);
    }
    public void setSheetData(){
        nodeCustomizer.setData(nodes[selectedNodes.get(0)].getData());
    }
    //endregion

    //region Text
    public void editText() {
        LayoutInflater li = LayoutInflater.from(getContext());
        View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(customDialogView);
        final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
        etName.setText(nodes[selectedNodes.get(0)].getText());
        etName.selectAll();
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                for(int i:selectedNodes) {
                nodes[i].setText(etName.getText().toString());
                }
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void setText(String text) {
        for (int i:selectedNodes) nodes[i].setText(text);
    }
    //endregion

    //region Save/Load
    public NodeData[] getData(){
        NodeData[] data=new NodeData[255];
        for(int i=0; i<255; i++){
            if(nodes[i]!=null){
                data[i]=nodes[i].getData();
            }
        }
        return data;
    }

    public void saveData(){
        if (mapName==null)
        {
            saveAs();
        }
        else{
            MapLoader loader=new MapLoader();
            if (loader.saveMap(getContext(), mapName ,getData())) {
                Toast.makeText(getContext(), "Map saved to \"" + mapName + "\"", Toast.LENGTH_SHORT).show();
                changed=false;
            }
            else Toast.makeText(getContext(), "Error: Cannot save map", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveAs(){
        LayoutInflater li = LayoutInflater.from(getContext());
        View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(customDialogView);
        final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
        ((TextView)customDialogView.findViewById(R.id.tv_dialog)).setText(R.string.map_name);
        etName.setText(nodes[selectedNodes.get(0)].getText());
        etName.selectAll();
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mapName=etName.getText().toString();
                final MapLoader loader=new MapLoader();
                if (loader.mapExist(mapName)){
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
                    alertDialog.setMessage("Map already exist. Do you want to overwrite?");
                    alertDialog.setIcon(R.mipmap.ic_launcher);
                    alertDialog.setPositiveButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    alertDialog.setNegativeButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (loader.saveMap(getContext(), mapName ,getData())) {
                                Toast.makeText(getContext(), "Map saved to \"" + mapName + "\"", Toast.LENGTH_SHORT).show();
                                changed=false;
                            }
                            else Toast.makeText(getContext(), "Error: Cannot save map", Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                        }
                    });
                    alertDialog.show();
                    return;
                }
                if (loader.saveMap(getContext(), mapName ,getData())) {
                    Toast.makeText(getContext(), "Map saved to \"" + mapName + "\"", Toast.LENGTH_SHORT).show();
                    changed=false;
                }
                else Toast.makeText(getContext(), "Error: Cannot save map", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void setMapName(String mapName){
        this.mapName=mapName;
    }
    public void loadMap(@Nullable String mapName){
        this.mapName=mapName;
        if(mapName==null) setMap(null);
        else {
            MapLoader loader=new MapLoader();
            NodeData[] data= loader.loadMap(mapName);
            if(data!=null) {
                Node[] nodes = new Node[255];
                for (int i = 0; i < 255; i++) {
                    if (data[i] != null) {
                        nodes[i] = new Node(getContext());
                        nodes[i].setData(data[i]);
                    }
                }
                setMap(nodes);
            }
            else Toast.makeText(getContext(), "Error: Cannot load map", Toast.LENGTH_SHORT).show();
        }
    }
    //endregion
}