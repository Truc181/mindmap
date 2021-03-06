package com.uit.mindmap.mapdrawer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.uit.mindmap.R;
import com.uit.mindmap.data.LinePreferences;
import com.uit.mindmap.data.NodePreferences;
import com.uit.mindmap.data.TextPreferences;
import com.uit.mindmap.maploader.MapLoader;
import com.uit.mindmap.data.NodeData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MapView extends RelativeLayout {
    private static final int maxNodeAmount = 255;
    private static final int undoAmount = 20;
    public int selectionMode;

    private class State {
        List<Integer> ids;
        NodeData[] data;

        public State(List<Integer> ids, NodeData[] data) {
            this.ids = ids;
            this.data = data;
        }

        public State(State state) {
            ids = new ArrayList<>(state.ids);
            data = new NodeData[ids.size()];
            for (int i = 0; i < state.ids.size(); i++) {
                if (state.data[i] == null) {
                    data[i] = new NodeData(nodes[ids.get(i)].data);
                } else {
                    if (nodes[state.ids.get(i)].deleted) {

                    } else data[i] = new NodeData(nodes[state.ids.get(i)].data);
                }
            }
        }
    }

    //region Listeners
    public interface onChangeListener {
        public void onChange(boolean undoAvailable, boolean redoAvailable);
    }

    private onChangeListener changeListener;

    public void setOnChangeListener(onChangeListener changeListener) {
        this.changeListener = changeListener;
    }
    //endregion

    Node[] nodes;
    ArrayList<Integer> selectedNodes;
    LinePaint paint;
    String mapName;
    NodeCustomizer nodeCustomizer;
    boolean changed = false;
    Deque<State> undoHistory;
    Deque<State> redoHistory;

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
        nodes = new Node[maxNodeAmount];
        paint = new LinePaint();
        paint.setAntiAlias(true);
        undoHistory = new ArrayDeque<>();
        redoHistory = new ArrayDeque<>();
    }

    public void setMap(@Nullable Node[] nodes) {
        if (nodes == null) {
            addNode(null);
        } else {
            this.nodes = nodes;
            for (Node node : nodes) {
                if (node != null) {
                    addView(node);
                    node.setMap(this);
                    node.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Node n = (Node) v;
                            selectNode(n.data.id);
                        }
                    });
                    node.applyData();
                }
            }
        }
        selectNode(0);
    }
    //endregion

    //region Undo/Redo
    public State getState() {
        NodeData[] data = new NodeData[selectedNodes.size()];
        List<Integer> ids = new ArrayList<>(selectedNodes);
        for (int i = 0; i < selectedNodes.size(); i++)
            if (!nodes[selectedNodes.get(i)].deleted)
                data[i] = new NodeData(nodes[selectedNodes.get(i)].data);
        return new State(ids, data);
    }

    public void setState(State state) {
        for (int i = 0; i < state.ids.size(); i++) {
            if (state.data[i] == null) {
                deselectAll();
                removeNode(state.ids.get(i));
                Log.i("undo", "Remove node " + state.ids.get(i));
            } else {
                if (nodes[state.ids.get(i)].deleted) {
                    Log.i("undo", "Load node " + state.ids.get(i));
                    loadNode(state.data[i]);
                    invalidate();
                } else {
                    nodes[state.ids.get(i)].setData(new NodeData(state.data[i]));
                }
            }
        }
    }

    public void releaseUndo(State state) {
        for (int i = 0; i < state.ids.size(); i++) {
            if (state.data[i] != null && nodes[state.ids.get(0)].deleted) {
                nodes[nodes[state.ids.get(i)].data.parent].removeChildren(nodes[state.ids.get(i)].data.id);
                nodes[state.ids.get(i)] = null;
            }
        }
    }

    public void releaseState(State state) {
        for (int i = 0; i < state.ids.size(); i++) {
            if (state.data[i] == null) {
                nodes[nodes[state.ids.get(i)].data.parent].removeChildren(nodes[state.ids.get(i)].data.id);
                nodes[state.ids.get(i)] = null;
            }
        }
    }

    public boolean undo() {
        if (!undoHistory.isEmpty()) {
            State state = undoHistory.pollLast();


            redoHistory.add(state);
            redoHistory.add(new State(state));
            setChanged();
            setState(state);
            Log.i("undo", "Remain " + undoHistory.size());
            return true;
        } else return false;
    }

    public boolean redo() {
        if (!redoHistory.isEmpty()) {
            State state = redoHistory.pollLast();


            undoHistory.add(redoHistory.pollLast());
            setChanged();
            setState(state);
            return true;
        } else return false;
    }

    public void addCommand() {
        undoHistory.add(getState());
        Log.i("command", "" + undoHistory.size());
        redoHistory.clear();
        setChanged();
        if (undoHistory.size() > undoAmount) {
            State state = undoHistory.pollFirst();
            releaseUndo(state);
        }

    }

    public void addCommandRemoveNode() {
        NodeData[] data = new NodeData[selectedNodes.size()];
        for (int i = 0; i < selectedNodes.size(); i++) {
            data[i] = new NodeData(nodes[selectedNodes.get(i)].data);
        }
        undoHistory.add(new State(new ArrayList<Integer>(selectedNodes), data));
        Log.i("command", "" + undoHistory.size());
        redoHistory.clear();
        setChanged();
        if (undoHistory.size() > undoAmount) {
            State state = undoHistory.pollFirst();
            releaseUndo(state);
        }

    }

    public void addCommandAddNode() {
        undoHistory.add(new State(new ArrayList<Integer>(selectedNodes), new NodeData[selectedNodes.size()]));
        Log.i("command addnode", selectedNodes.size() + "");
        clearRedo();
        setChanged();
        if (undoHistory.size() > undoAmount) {
            State state = undoHistory.pollFirst();
            releaseUndo(state);
        }
    }

    private void clearRedo() {
        for (int i = 0; i < redoHistory.size() / 2; i++) {
            State state = redoHistory.pollLast();
            releaseState(state);
            redoHistory.pollLast();
        }
    }

    public void setChanged() {

        changed = true;
        if (changeListener != null) {
            Log.i("onChange", "setChanged");
            changeListener.onChange(!undoHistory.isEmpty(), !redoHistory.isEmpty());
        }
    }
    //endregion

    //region Draw
    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < maxNodeAmount; i++) {
            if (nodes[i] != null && nodes[i].data != null) {
                for (int a : nodes[i].data.children) {
                    if (!nodes[a].deleted)
                        paint.drawConnection(nodes[i], nodes[a], canvas);
                }
            }
        }
        super.onDraw(canvas);
    }

    //endregion

    //region Add
    public int addNode(@Nullable int[] pos) {
        int i = 0;
        while (nodes[i] != null && !nodes[i].deleted && i < maxNodeAmount-1) {
            i++;
        }
        if (i >= maxNodeAmount-1) {
            return -1;
        }
        Node node = new Node(getContext());
        addView(node);
        nodes[i] = node;
        node.data.id = i;
        node.setMap(this);
        selectNode(i);
        if (pos != null) {
            node.setPosition(pos);
        } else {
            pos = new int[2];
            pos[0] += getContext().getResources().getDimension(R.dimen.map_size) / 2;
            pos[1] += getContext().getResources().getDimension(R.dimen.map_size) / 2;
            node.setPosition(pos);
            node.setText("Root node");
            int color = getContext().getResources().getColor(R.color.colorPrimary);
            node.setNodePreferences(new NodePreferences(color, Color.WHITE, 0));
            node.setTextSize(7);
            node.setTextColor(Color.WHITE);
        }
        node.applyData();
        node.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Node n = (Node) v;
                selectNode(n.data.id);
            }
        });
        return i;
    }

    public int addNode(int parent) {
        int i = 0;
        while (nodes[i] != null && i < maxNodeAmount-1) {
            i++;
        }
        if (i >= maxNodeAmount-1) {
            return -1;
        }
        Node node = new Node(getContext());
        addView(node);
        node.data.parent = parent;
        node.data.id = i;
        nodes[i] = node;
        node.setText("New node");
        node.setMap(this);
        node.applyData();
        nodes[parent].addChild(i);
        int[] pos = new int[2];
        pos[0] = nodes[parent].data.pos[0];
        pos[1] = nodes[parent].data.pos[1];
        pos[0] += 50 + nodes[parent].getWidth();
        node.setPosition(pos);
        node.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Node n = (Node) v;
                selectNode(n.data.id);
            }
        });
        return i;
    }

    public void addNode() {
        List<Integer> newNodes = new ArrayList<>();
        for (int i : selectedNodes) {
            int node = addNode(i);
            if (node == -1) {
                Toast.makeText(getContext(), "Reach maximum amount of nodes", Toast.LENGTH_SHORT).show();
                if (newNodes.size() > 0) {
                    for (int j : newNodes) {
                        selectMultiple(i);
                    }
                    addCommandAddNode();
                    LayoutInflater li = LayoutInflater.from(getContext());
                    View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
                    alertDialogBuilder.setView(customDialogView);
                    final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
                    etName.setText(nodes[selectedNodes.get(0)].data.text);
                    etName.selectAll();
                    alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            for (int i : selectedNodes) {
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
                    Log.i("command", "" + undoHistory.size());
                }
                return;
            }
            newNodes.add(node);
            nodes[node].setText("New node");
        }
        deselectAll();
        for (int i : newNodes) {
            selectMultiple(i);
        }
        addCommandAddNode();
        LayoutInflater li = LayoutInflater.from(getContext());
        View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(customDialogView);
        final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
        etName.setText(nodes[selectedNodes.get(0)].data.text);
        etName.selectAll();
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                for (int i : selectedNodes) {
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
        Log.i("command", "" + undoHistory.size());
    }

    public void loadNode(NodeData data) {
        nodes[data.id].deleted = false;
        addView(nodes[data.id]);
    }
    //endregion

    //region Remove
    public ArrayList<Integer> removeChildNode(int id) {
        ArrayList<Integer> a = new ArrayList<>();
        a.add((Integer) id);
        for (int i : nodes[id].data.children) a.addAll(removeChildNode(i));
        nodes[id].defocus();
        removeView(nodes[id]);
        nodes[id].deleted = true;
        return a;
    }

    public ArrayList<Integer> removeNode(int id) {
        ArrayList<Integer> a = new ArrayList<>();
        a.add((Integer) id);
        Log.i("remove", "" + id);
        if (id != 0) {
            for (int i : nodes[id].data.children) a.addAll(removeChildNode(i));
            nodes[id].defocus();
            removeView(nodes[id]);
            nodes[id].deleted = true;
        } else Toast.makeText(getContext(), "Cannot delete root node", Toast.LENGTH_SHORT).show();
        return a;
    }

    public void removeNode() {
        ArrayList<Integer> a = new ArrayList<>();
        for (int i : selectedNodes) {
            a.addAll(removeNode(i));
        }
        selectedNodes = a;
        Log.i("selected", a.size() + "");
        if (selectedNodes.size() != 1 || selectedNodes.get(0) != 0) {
            addCommandRemoveNode();
            Log.i("Undo delete", a.size() + "");
        }
        selectedNodes.clear();
    }

    //endregion

    //region Selection
    public void deselectNode(int id) {
        nodes[id].defocus();
        selectedNodes.remove((Integer) id);
    }

    public void deselectAll() {
        for (int id : selectedNodes) {
            nodes[id].defocus();
        }
        ((MapDrawerActivity) getContext()).deselect();
        selectedNodes.clear();
    }

    public void selectSingle(int id) {
        deselectAll();
        selectedNodes.add(id);
        nodes[id].focus();
        nodes[id].bringToFront();
        ((MapDrawerActivity) getContext()).select();
    }

    public void selectMultiple(int id) {
        int index = selectedNodes.indexOf(id);
        if (index <= -1) {
            selectedNodes.add(id);
            nodes[id].focus();
            nodes[id].bringToFront();
            ((MapDrawerActivity) getContext()).select();
        }
    }

    public void selectNode(int id) {
        switch (selectionMode) {
            case 0:
                selectSingle(id);
                break;
            case 1:
                toggleSelect(id);
                break;
        }
    }

    public void toggleSelect(int id) {
        int index = selectedNodes.indexOf(id);
        if (index > -1) deselectNode(selectedNodes.get(index));
        else {
            selectedNodes.add(id);
            nodes[id].focus();
            nodes[id].bringToFront();
            ((MapDrawerActivity) getContext()).select();
        }
    }

    public void rectangleSelect(int[] start, int[] end) {
        for (int i = 0; i < maxNodeAmount; i++) {
            if (nodes[i] != null) {
                if (nodes[i].data.pos[0] < Math.max(start[0], end[0])
                        && nodes[i].data.pos[0] > Math.min(start[0], end[0])
                        && nodes[i].data.pos[1] < Math.max(start[1], end[1])
                        && nodes[i].data.pos[1] > Math.min(start[1], end[1])) {
                    selectMultiple(i);
                }
            }
        }
    }
    //endregion

    //region Customization

    public void setNodeCustomizer(NodeCustomizer customizer) {
        nodeCustomizer = customizer;
        customizer.setMapView(this);
    }

    public void setNodePreferences(NodePreferences preferences) {
        addCommand();
        for (int i : selectedNodes) nodes[i].setNodePreferences(preferences);
    }

    public void setTextPreferences(TextPreferences preferences) {
        addCommand();
        for (int i : selectedNodes) nodes[i].setTextPreferences(preferences);
    }

    public void setLinePreferences(LinePreferences preferences) {
        addCommand();
        for (int i : selectedNodes) nodes[i].setLinePreferences(preferences);
    }

    //endregion

    //region Text
    public void editText() {

        LayoutInflater li = LayoutInflater.from(getContext());
        View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(customDialogView);
        final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
        etName.setText(nodes[selectedNodes.get(0)].data.text);
        etName.selectAll();
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                addCommand();
                for (int i : selectedNodes) {
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
        for (int i : selectedNodes) nodes[i].setText(text);
    }

    public void moveNode(int x, int y) {
        for (int i : selectedNodes) {
            nodes[i].movePosition(x, y);
        }
    }
    //endregion

    //region Save/Load
    public NodeData[] getData() {
        NodeData[] data = new NodeData[maxNodeAmount];
        for (int i = 0; i < maxNodeAmount; i++) {
            if (nodes[i] != null && !nodes[i].deleted) {
                data[i] = new NodeData(nodes[i].data);
                for (int a : nodes[i].data.children) {
                    if (nodes[a] == null || nodes[a].deleted)
                        data[i].children.remove(data[i].children.indexOf(a));
                }
            }
        }
        return data;
    }

    public NodeData getFirstData() {
        if (selectedNodes != null && selectedNodes.size() > 0)
            return nodes[selectedNodes.get(0)].data;
        return null;
    }

    public void saveData() {
        deselectAll();
        if (mapName == null) {
            saveAs();
        } else {
            MapLoader loader = new MapLoader(getContext());
            if (loader.saveMap(mapName, getData())) {
                Toast.makeText(getContext(), "Map saved to \"" + mapName + "\"", Toast.LENGTH_SHORT).show();
                loader.saveThumbnail(mapName, getThumbnail());
                changed = false;
            } else
                Toast.makeText(getContext(), "Error: Cannot save map", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveAs() {
        LayoutInflater li = LayoutInflater.from(getContext());
        View customDialogView = li.inflate(R.layout.edit_text_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setView(customDialogView);
        final EditText etName = (EditText) customDialogView.findViewById(R.id.name);
        ((TextView) customDialogView.findViewById(R.id.tv_dialog)).setText(R.string.map_name);
        etName.setText("New map");
        etName.selectAll();
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null);
        final AlertDialog saveDialog = alertDialogBuilder.create();
        saveDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                saveDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final MapLoader loader = new MapLoader(getContext());
                        if (loader.mapExist(etName.getText().toString())) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
                            alertDialog.setMessage("Map already exist. Do you want to overwrite?");
                            alertDialog.setIcon(R.mipmap.ic_launcher);
                            alertDialog.setPositiveButton(R.string.no, null);
                            alertDialog.setNegativeButton(R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    mapName = etName.getText().toString();
                                    if (loader.saveMap(mapName, getData())) {
                                        loader.saveThumbnail(mapName, getThumbnail());
                                        Toast.makeText(getContext(), "Map saved to \"" + mapName + "\"", Toast.LENGTH_SHORT).show();
                                        changed = false;
                                    } else
                                        Toast.makeText(getContext(), "Error: Cannot save map", Toast.LENGTH_SHORT).show();
                                    dialog.cancel();
                                    saveDialog.dismiss();
                                }
                            });
                            alertDialog.show();
                            return;
                        }
                        mapName = etName.getText().toString();
                        if (loader.saveMap(mapName, getData())) {
                            loader.saveThumbnail(mapName, getThumbnail());
                            changed = false;
                        }
                        dialog.dismiss();
                    }
                });
            }
        });
        saveDialog.show();
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public void loadMap(@Nullable String mapName) {
        this.mapName = mapName;
        if (mapName == null) setMap(null);
        else {
            MapLoader loader = new MapLoader(getContext());
            NodeData[] data = loader.loadMap(mapName);
            if (data != null) {
                Node[] nodes = new Node[maxNodeAmount];
                for (int i = 0; i < maxNodeAmount; i++) {
                    if (data[i] != null) {
                        nodes[i] = new Node(getContext());
                        nodes[i].setData(data[i]);
                    }
                }
                setMap(nodes);
            }
        }
    }

    //endregion

    //regionDimensions
    private int[] getMapCenter() {
        int[] a = new int[2];
        int maxX = nodes[0].data.pos[0];
        int maxY = nodes[0].data.pos[1];
        int minX = nodes[0].data.pos[0];
        int minY = nodes[0].data.pos[1];
        for (Node node : nodes) {
            if (node != null && !node.deleted) {
                if (node.data.pos[0] > maxX) maxX = node.data.pos[0];
                if (node.data.pos[1] > maxY) maxY = node.data.pos[1];
                if (node.data.pos[0] < minX) minX = node.data.pos[0];
                if (node.data.pos[1] < minY) minY = node.data.pos[1];
            }
        }
        a[0] = (maxX + minX) / 2;
        a[1] = (maxY + minY) / 2;
        return a;
    }

    private int getMapWidth() {
        int max = nodes[0].data.pos[0];
        int min = nodes[0].data.pos[0];
        for (Node node : nodes) {
            if (node != null && !node.deleted) {
                if (node.data.pos[0] > max) max = node.data.pos[0];
                if (node.data.pos[0] < min) min = node.data.pos[0];
            }
        }
        return (max + min) / 2;
    }

    private int getMapHeight() {
        int max = nodes[0].data.pos[1];
        int min = nodes[0].data.pos[1];
        for (Node node : nodes) {
            if (node != null && !node.deleted) {
                if (node.data.pos[1] > max) max = node.data.pos[1];
                if (node.data.pos[1] < min) min = node.data.pos[1];
            }
        }
        return (max + min) / 2;
    }

    private int[] getMapDimensions() {
        int[] a = new int[4];
        int maxX = nodes[0].data.pos[0];
        int maxY = nodes[0].data.pos[1];
        int minX = nodes[0].data.pos[0];
        int minY = nodes[0].data.pos[1];
        for (Node node : nodes) {
            if (node != null && !node.deleted) {
                int[] bounds = node.bounds();
                if (bounds[0] < minX) minX = bounds[0];
                if (bounds[1] < minY) minY = bounds[1];
                if (bounds[2] > maxX) maxX = bounds[2];
                if (bounds[3] > maxY) maxY = bounds[3];
            }
        }
        a[0] = minX - 10;
        a[1] = minY - 10;
        a[2] = maxX + 10;
        a[3] = maxY + 10;
        return a;
    }

    public Bitmap getThumbnail() {
        ((MapDrawerActivity) getContext()).deselect();
        int[] d = getMapDimensions();
        int[] a = getMapCenter();
        int w = d[2] - d[0];
        int h = d[3] - d[1];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(-d[0], -d[1]);
        this.layout(0, 0, getWidth(), getHeight());
        Log.i("Left", "" + d[0]);
        Log.i("Top", "" + d[1]);
        Log.i("Right", "" + d[2]);
        Log.i("Bottom", "" + d[3]);
        this.draw(c);
        int width = Math.max(h * 4 / 3, w);
        int height = Math.max(w * 3 / 4, h);
        Bitmap x = getCorrectBitmap(b, width, height);
        Bitmap b1 = Bitmap.createScaledBitmap(x, 400, 300, true);
        x.recycle();
        return b1;
    }

    public boolean isRootSelected() {
        return (selectedNodes.size() == 1 && selectedNodes.get(0) == 0);
    }

    private Bitmap getCorrectBitmap(Bitmap scr, int width, int height) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        int w = scr.getWidth();
        int h = scr.getHeight();
        scr.getPixels(pixels, 0, w, 0, 0, w, h);
        b.setPixels(pixels, 0, w, (width - w) / 2, (height - h) / 2, w, h);
        scr.recycle();
        return b;
    }
    //endregion

    public void setSelectionMode(int selectionMode) {
        this.selectionMode = selectionMode;
        Log.i("selection mode", "" + selectionMode);
    }
}
