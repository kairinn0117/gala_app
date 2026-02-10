package com.example.galafunctions;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class RecyclerItemClickListener implements RecyclerView.OnItemTouchListener {

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(View view, int position);
    }

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final GestureDetector gestureDetector;
    private final RecyclerView recyclerView;

    public RecyclerItemClickListener(Context context, RecyclerView recyclerView,
                                     OnItemClickListener clickListener,
                                     OnItemLongClickListener longClickListener) {
        this.recyclerView = recyclerView;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) { return true; }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                if (child == null || longClickListener == null) return;

                int pos = recyclerView.getChildAdapterPosition(child);
                if (pos != RecyclerView.NO_POSITION) {
                    longClickListener.onItemLongClick(child, pos);
                }
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        View child = rv.findChildViewUnder(e.getX(), e.getY());
        if (child == null || clickListener == null) return false;

        if (gestureDetector.onTouchEvent(e)) {
            int pos = rv.getChildAdapterPosition(child);
            if (pos != RecyclerView.NO_POSITION) {
                clickListener.onItemClick(child, pos);
                return true;
            }
        }
        return false;
    }

    @Override public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}
    @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
}