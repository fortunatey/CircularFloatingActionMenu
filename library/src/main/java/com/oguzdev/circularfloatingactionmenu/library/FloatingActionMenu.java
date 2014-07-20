/*
 *   Copyright 2014 Oguz Bilgener
 */
package com.oguzdev.circularfloatingactionmenu.library;

import android.app.Activity;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.oguzdev.circularfloatingactionmenu.library.animation.DefaultAnimationHandler;
import com.oguzdev.circularfloatingactionmenu.library.animation.MenuAnimationHandler;

import java.util.ArrayList;

/**
 * Provides the main structure of the menu.
 */

public class FloatingActionMenu {

    /** Reference to the view (usually a button) to trigger the menu to show */
    private View mainActionView;
    /** The angle (in degrees, modulus 360) which the circular menu starts from  */
    private int startAngle;
    /** The angle (in degrees, modulus 360) which the circular menu ends at  */
    private int endAngle;
    /** Distance of menu items from mainActionView */
    private int radius;
    /** List of menu items */
    private ArrayList<Item> subActionItems;
    /** Reference to the preferred {@link MenuAnimationHandler} object */
    private MenuAnimationHandler animationHandler;
    /**  */
    private boolean animated;
    /** */
    private boolean open;

    /**
     * Constructor that takes the parameters collected using {@link FloatingActionMenu.Builder}
     * @param mainActionView
     * @param startAngle
     * @param endAngle
     * @param radius
     * @param subActionItems
     * @param animationHandler
     * @param animated
     */
    public FloatingActionMenu(View mainActionView,
                              int startAngle,
                              int endAngle,
                              int radius,
                              ArrayList<Item> subActionItems,
                              MenuAnimationHandler animationHandler,
                              boolean animated) {
        this.mainActionView = mainActionView;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        this.radius = radius;
        this.subActionItems = subActionItems;
        this.animationHandler = animationHandler;
        this.animated = animated;
        // The menu is initially closed.
        this.open = false;

        // Listen click events on the main action view
        // In the future, touch and drag events could be listened to offer an alternative behaviour
        this.mainActionView.setClickable(true);
        this.mainActionView.setOnClickListener(new ActionViewClickListener());

        // Do not forget to set the menu as self to our customizable animation handler
        if(animationHandler != null) {
            animationHandler.setMenu(this);
        }

        // Find items with undefined sizes
        for(final Item item : subActionItems) {
            if(item.width == 0 || item.height == 0) {
                // Figure out the size by temporarily adding it to the Activity content view hierarchy
                // and ask the size from the system
                ((ViewGroup) getActivityContentView()).addView(item.view);
                // Make item view invisible, just in case
                item.view.setAlpha(0);
                // Wait for the right time
                item.view.post(new Runnable() {
                    @Override
                    public void run() {
                        // Measure the size of the item view
                        item.width = item.view.getMeasuredWidth();
                        item.height = item.view.getMeasuredHeight();

                        // Revert everything back to normal
                        item.view.setAlpha(1);
                        // Remove the item view from view hierarchy
                        ((ViewGroup) getActivityContentView()).removeView(item.view);
                    }
                });
            }
        }
    }

    /**
     * Simply opens the menu by doing necessary calculations.
     * @param animated if true, this action is executed by the current {@link MenuAnimationHandler}
     */
    public void open(boolean animated) {
        // Find the center of the action view
        Point center = getActionViewCenter();
        // populate destination x,y coordinates of Items
        calculateItemPositions();

        if(animated && animationHandler != null) {
            // If animations are enabled and we have a MenuAnimationHandler, let it do the heavy work
            if(animationHandler.isAnimating()) {
                // Do not proceed if there is an animation currently going on.
                return;
            }

            for (int i = 0; i < subActionItems.size(); i++) {
                // It is required that these Item views are not currently added to any parent
                // Because they are supposed to be added to the Activity content view,
                // just before the animation starts
                if (subActionItems.get(i).view.getParent() != null) {
                    throw new RuntimeException("All of the sub action items have to be independent from a parent.");
                }
                // Initially, place all items right at the center of the main action view
                // Because they are supposed to start animating from that point.
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(subActionItems.get(i).width, subActionItems.get(i).height, Gravity.TOP | Gravity.LEFT);
                params.setMargins(center.x - subActionItems.get(i).width / 2, center.y - subActionItems.get(i).height / 2, 0, 0);
                //
                ((ViewGroup) getActivityContentView()).addView(subActionItems.get(i).view, params);
            }
            // Tell the current MenuAnimationHandler to animate from the center
            animationHandler.animateMenuOpening(center);
        }
        else {
            // If animations are disabled, just place each of the items to their calculated destination positions.
            for (int i = 0; i < subActionItems.size(); i++) {
                // This is currently done by giving them large margins
                final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(subActionItems.get(i).width, subActionItems.get(i).height, Gravity.TOP | Gravity.LEFT);
                params.setMargins(subActionItems.get(i).x, subActionItems.get(i).y, 0, 0);
                subActionItems.get(i).view.setLayoutParams(params);
                // Because they are placed into the main content view of the Activity,
                // which is itself a FrameLayout
                ((ViewGroup) getActivityContentView()).addView(subActionItems.get(i).view, params);
            }
        }
        // do not forget to specify that the menu is open.
        open = true;
    }

    /**
     * Closes the menu.
     * @param animated if true, this action is executed by the current {@link MenuAnimationHandler}
     */
    public void close(boolean animated) {
        // If animations are enabled and we have a MenuAnimationHandler, let it do the heavy work
        if(animated && animationHandler != null) {
            if(animationHandler.isAnimating()) {
                // Do not proceed if there is an animation currently going on.
                return;
            }
            animationHandler.animateMenuClosing(getActionViewCenter());
        }
        else {
            // If animations are disabled, just detach each of the Item views from the Activity content view.
            for (int i = 0; i < subActionItems.size(); i++) {
                ((ViewGroup) getActivityContentView()).removeView(subActionItems.get(i).view);
            }
        }
        // do not forget to specify that the menu is now closed.
        open = false;
    }

    /**
     * Toggles the menu
     * @param animated if true, the open/close action is executed by the current {@link MenuAnimationHandler}
     */
    public void toggle(boolean animated) {
        if(open) {
            close(animated);
        }
        else {
            open(animated);
        }
    }

    /**
     * @return whether the menu is open or not
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Gets the coordinates of the main action view
     * This method should only be called after the main layout of the Activity is drawn,
     * such as when a user clicks the action button.
     * @return a Point containing x and y coordinates of the top left corner of action view
     */
    private Point getActionViewCoordinates() {
        int[] coords = new int[2];
        // This method returns a x and y values that can be larger than the dimensions of the device screen.
        mainActionView.getLocationOnScreen(coords);
        // So, we need to deduce the offsets.
        coords[0] -= (getScreenSize().x - getActivityContentView().getMeasuredWidth());
        coords[1] -= (getScreenSize().y - getActivityContentView().getMeasuredHeight());
        return new Point(coords[0], coords[1]);
    }

    /**
     * Returns the center point of the main action view
     * @return
     */
    public Point getActionViewCenter() {
        Point point = getActionViewCoordinates();
        point.x += mainActionView.getMeasuredWidth() / 2;
        point.y += mainActionView.getMeasuredHeight() / 2;
        return point;
    }

    /**
     * Calculates the desired positions of all items.
     */
    private void calculateItemPositions() {
        // Create an arc that starts from startAngle and ends at endAngle
        // in an area that is as large as 4*radius^2
        Point center = getActionViewCenter();
        RectF area = new RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius);

        Path orbit = new Path();
        orbit.addArc(area, startAngle, endAngle - startAngle);

        PathMeasure measure = new PathMeasure(orbit, false);

        // Measure this path, in order to find points that have the same distance between each other
        for(int i=0; i<subActionItems.size(); i++) {
            float[] coords = new float[] {0f, 0f};
            measure.getPosTan((i) * measure.getLength() / (subActionItems.size()-1), coords, null);
            // get the x and y values of these points and set them to each of sub action items.
            subActionItems.get(i).x = (int) coords[0] - subActionItems.get(i).width / 2;
            subActionItems.get(i).y = (int) coords[1] - subActionItems.get(i).height / 2;
        }
    }

    /**
     * @return the specified raduis of the menu
     */
    public int getRadius() {
        return radius;
    }

    /**
     * @return a reference to the sub action items list
     */
    public ArrayList<Item> getSubActionItems() {
        return subActionItems;
    }

    /**
     * Finds and returns the main content view from the Activity context.
     * @return the main content view
     */
    public View getActivityContentView() {
        return ((Activity)mainActionView.getContext()).getWindow().getDecorView().findViewById(android.R.id.content);
    }

    /**
     * Retrieves the screen size from the Activity context
     * @return the screen size as a Point object
     */
    private Point getScreenSize() {
        Point size = new Point();
        ((Activity)mainActionView.getContext()).getWindowManager().getDefaultDisplay().getSize(size);
        return size;
    }

    /**
     * A simple click listener used by the main action view
     */
    public class ActionViewClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            toggle(animated);
        }
    }

    /**
     * A simple structure to put a view and its x, y, width and height values together
     */
    public static class Item {
        public int x;
        public int y;
        public int width;
        public int height;

        public View view;

        public Item(View view, int width, int height) {
            this.view = view;
            this.width = width;
            this.height = height;
            x = 0;
            y = 0;
        }
    }

    /**
     * A builder for {@link FloatingActionMenu} in conventional Java Builder format
     */
    public static class Builder {

        private int startAngle;
        private int endAngle;
        private int radius;
        private View actionView;
        private ArrayList<Item> subActionItems;
        private MenuAnimationHandler animationHandler;
        private boolean animated;

        public Builder(Activity activity) {
            subActionItems = new ArrayList<Item>();

            radius = activity.getResources().getDimensionPixelSize(R.dimen.action_menu_radius);
            startAngle = 180;
            endAngle = 270;
            animationHandler = new DefaultAnimationHandler();
            animated = true;
        }

        public Builder setStartAngle(int startAngle) {
            this.startAngle = startAngle;
            return this;
        }

        public Builder setEndAngle(int endAngle) {
            this.endAngle = endAngle;
            return this;
        }

        public Builder setRadius(int radius) {
            this.radius = radius;
            return this;
        }

        public Builder addSubActionView(View subActionView, int width, int height) {
            subActionItems.add(new Item(subActionView, width, height));
            return this;
        }

        public Builder addSubActionView(View subActionView) {
            return this.addSubActionView(subActionView, 0, 0);
        }

        public Builder setAnimationHandler(MenuAnimationHandler animationHandler) {
            this.animationHandler = animationHandler;
            return this;
        }

        public Builder enableAnimations() {
            animated = true;
            return this;
        }

        public Builder disableAnimations() {
            animated = false;
            return this;
        }

        public Builder attachTo(View actionView) {
            this.actionView = actionView;
            return this;
        }

        public FloatingActionMenu build() {
            return new FloatingActionMenu(actionView,
                                          startAngle,
                                          endAngle,
                                          radius,
                                          subActionItems,
                                          animationHandler,
                                          animated);
        }
    }
}