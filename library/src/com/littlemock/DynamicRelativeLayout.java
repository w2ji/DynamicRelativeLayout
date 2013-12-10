package com.littlemock;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;

public class DynamicRelativeLayout extends ViewGroup {
    protected HashMap<String, ChildGraphViewNode> childViewMap = new HashMap<String, ChildGraphViewNode>();

    public DynamicRelativeLayout(Context context) {
        super(context);
    }

    public DynamicRelativeLayout(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public DynamicRelativeLayout(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);
    }


    /*
    DEFINITION FOR STATES:
    UNEXPLORED - node has not been visited
    DISCOVERED - node has been visited but waiting for child to finish
    EXPLORED - node's child has finished and the node has been explored
    DONE - node has been measured
     */
    public enum State {
        UNEXPLORED, DISCOVERED, EXPLORED, DONE
    }

    public enum Direction {
        LEFT, TOP, RIGHT, BOTTOM, NONE
    }

    protected static class ChildGraphViewNode {
        State state = State.UNEXPLORED;
        View childView;

        public ChildGraphViewNode(View childView) {
            this.childView = childView;
        }
    }

    protected static class ChildGraphViewNodeWrapper {
        ChildGraphViewNode childGraphViewNode;
        Direction direction;

        public ChildGraphViewNodeWrapper(ChildGraphViewNode childGraphViewNode, Direction direction) {
            this.childGraphViewNode = childGraphViewNode;
            this.direction = direction;
        }
    }

    public ArrayList<ChildGraphViewNodeWrapper> getNeighborViews(ChildGraphViewNode currentChild) {
        ChildGraphViewNode childMapObject = childViewMap.get(String.valueOf(currentChild.childView.getId()));
        LayoutParams lp = (LayoutParams) childMapObject.childView.getLayoutParams();
        ArrayList<ChildGraphViewNodeWrapper> childList = new ArrayList<ChildGraphViewNodeWrapper>();
        if (lp.dynamicBaseRefLeft != -1) {
            childList.add(new ChildGraphViewNodeWrapper(childViewMap.get(String.valueOf(lp.dynamicBaseRefLeft)), Direction.LEFT));
        }
        if (lp.dynamicBaseRefTop != -1) {
            childList.add(new ChildGraphViewNodeWrapper(childViewMap.get(String.valueOf(lp.dynamicBaseRefTop)), Direction.TOP));
        }
        if (lp.dynamicBaseRefRight != -1) {
            childList.add(new ChildGraphViewNodeWrapper(childViewMap.get(String.valueOf(lp.dynamicBaseRefRight)), Direction.RIGHT));
        }
        if (lp.dynamicBaseRefBottom != -1) {
            childList.add(new ChildGraphViewNodeWrapper(childViewMap.get(String.valueOf(lp.dynamicBaseRefBottom)), Direction.BOTTOM));
        }
        return childList;
    }

    public boolean detectCycleInGraph(ChildGraphViewNode node) {
        if (node.state.equals(State.EXPLORED))
            return false;
        node.state = State.DISCOVERED;

        for (ChildGraphViewNodeWrapper obj : getNeighborViews(node)) {
            if (obj.childGraphViewNode.state.equals(State.UNEXPLORED)) {
                final boolean result = detectCycleInGraph(obj.childGraphViewNode);
                if (result)
                    return result;
            } else if (obj.childGraphViewNode.state.equals(State.EXPLORED)) {
                return false;
            } else {
                return true;
            }
        }
        node.state = State.EXPLORED;
        return false;
    }

    public ChildGraphViewNodeWrapper getChildByDirection(ArrayList<ChildGraphViewNodeWrapper> relativeViews, Direction direction) {
        for (ChildGraphViewNodeWrapper nodeWrapper : relativeViews) {
            if (nodeWrapper.direction.equals(direction)) {
                return nodeWrapper;
            }
        }
        return null;
    }

    public int recursiveOnMeasure(int widthMeasureSpec, int heightMeasureSpec, ChildGraphViewNode node, Direction direction) {
        LayoutParams lp = (LayoutParams) node.childView.getLayoutParams();
        final ArrayList<ChildGraphViewNodeWrapper> relativeViews = getNeighborViews(node);
        if (node.state.equals(State.DONE)) {
            if (Direction.LEFT.equals(direction)) {
                return lp.getChildMarginLeft(node.childView.getMeasuredWidth(), getMeasuredWidth()) + node.childView.getMeasuredWidth();
            } else if (Direction.TOP.equals(direction)) {
                return lp.getChildMarginTop(node.childView.getMeasuredHeight(), getMeasuredHeight()) + node.childView.getMeasuredHeight();
            } else if (Direction.RIGHT.equals(direction)) {
                return lp.getChildMarginLeft(node.childView.getMeasuredWidth(), getMeasuredWidth());
            } else if (Direction.BOTTOM.equals(direction)) {
                return lp.getChildMarginTop(node.childView.getMeasuredHeight(), getMeasuredHeight());
            } else {
                return 0;
            }
        }

        // IF we are here then the current view has not been measured
        // we will measure the current child view if it doesnt have any reference to
        // other child views
        if (relativeViews.size() == 0) {
            if (node.childView.getVisibility() == View.GONE) {
                if (Direction.LEFT.equals(direction)) {
                    return 0;
                } else if (Direction.TOP.equals(direction)) {
                    return 0;
                } else if (Direction.RIGHT.equals(direction)) {
                    return this.getMeasuredWidth();
                } else if (Direction.BOTTOM.equals(direction)) {
                    return this.getMeasuredHeight();
                } else {
                    return 0;
                }
            } else {
                // This view is not relative to any other child, it will measure itself
                int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        lp.getLeftMargin(getMeasuredWidth()) + lp.getRightMargin(getMeasuredWidth()),
                        lp.getDynamicWidth(getMeasuredWidth()));
                int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        lp.getTopMargin(getMeasuredHeight()) + lp.getBottomMargin(getMeasuredHeight()),
                        lp.getDynamicHeight(getMeasuredHeight()));
                node.childView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                node.state = State.DONE;
                if (Direction.LEFT.equals(direction)) {
                    return lp.getLeftMargin(getMeasuredWidth()) + node.childView.getMeasuredWidth();
                } else if (Direction.TOP.equals(direction)) {
                    return lp.getTopMargin(getMeasuredHeight()) + node.childView.getMeasuredHeight();
                } else if (Direction.RIGHT.equals(direction)) {
                    return lp.getLeftMargin(getMeasuredWidth());
                } else if (Direction.BOTTOM.equals(direction)) {
                    return lp.getTopMargin(getMeasuredHeight());
                } else {
                    return 0;
                }
            }
        } else {
            // if we get here we are relative to other childs
            if (node.childView.getVisibility() == View.GONE) {
                ChildGraphViewNodeWrapper nodeWrapper = getChildByDirection(relativeViews, direction);
                if (nodeWrapper != null) {
                    return recursiveOnMeasure(widthMeasureSpec, heightMeasureSpec, nodeWrapper.childGraphViewNode, direction);
                } else {
                    if (Direction.LEFT.equals(direction)) {
                        return 0;
                    } else if (Direction.TOP.equals(direction)) {
                        return 0;
                    } else if (Direction.RIGHT.equals(direction)) {
                        return this.getMeasuredWidth();
                    } else if (Direction.BOTTOM.equals(direction)) {
                        return this.getMeasuredHeight();
                    } else {
                        return 0;
                    }
                }
            } else {
                HashMap<Direction, Integer> marginMapping = new HashMap<Direction, Integer>();
                // we need to measure ourselves based on the child we depend on
                for (ChildGraphViewNodeWrapper nodeWrapper : relativeViews) {
                    marginMapping.put(nodeWrapper.direction, new Integer(
                            recursiveOnMeasure(widthMeasureSpec, heightMeasureSpec, nodeWrapper.childGraphViewNode, nodeWrapper.direction)
                    ));
                }

                if (marginMapping.containsKey(Direction.LEFT)) {
                    lp.dynamicRelativeMarginLeft = new Integer(lp.getLeftMargin(getMeasuredWidth()) + marginMapping.get(Direction.LEFT).intValue());
                }
                if (marginMapping.containsKey(Direction.TOP)) {
                    lp.dynamicRelativeMarginTop = new Integer(lp.getTopMargin(getMeasuredHeight()) + marginMapping.get(Direction.TOP).intValue());
                }
                if (marginMapping.containsKey(Direction.RIGHT)) {
                    lp.dynamicRelativeMarginRight = new Integer(marginMapping.get(Direction.RIGHT).intValue() - lp.getRightMargin(getMeasuredWidth()));
                }
                if (marginMapping.containsKey(Direction.BOTTOM)) {
                    lp.dynamicRelativeMarginBottom = new Integer(marginMapping.get(Direction.BOTTOM).intValue() - lp.getBottomMargin(getMeasuredHeight()));
                }

                int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        lp.getLeftMargin(getMeasuredWidth()) + lp.getRightMargin(getMeasuredWidth()),
                        lp.getDynamicWidth(getMeasuredWidth()));
                int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        lp.getTopMargin(getMeasuredHeight()) + lp.getBottomMargin(getMeasuredHeight()),
                        lp.getDynamicHeight(getMeasuredHeight()));
                node.childView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                node.state = State.DONE;
                if (Direction.LEFT.equals(direction)) {
                    return lp.getLeftMargin(getMeasuredWidth()) + node.childView.getMeasuredWidth();
                } else if (Direction.TOP.equals(direction)) {
                    return lp.getTopMargin(getMeasuredHeight()) + node.childView.getMeasuredHeight();
                } else if (Direction.RIGHT.equals(direction)) {
                    return lp.getLeftMargin(getMeasuredWidth());
                } else if (Direction.BOTTOM.equals(direction)) {
                    return lp.getTopMargin(getMeasuredHeight());
                } else {
                    return 0;
                }
            }
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        childViewMap = new HashMap<String, ChildGraphViewNode>();
        final int count = getChildCount();
        // first run put all child into hashmap
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.dynamicRelativeMarginLeft = null;
            lp.dynamicRelativeMarginTop = null;
            lp.dynamicRelativeMarginRight = null;
            lp.dynamicRelativeMarginBottom = null;
            ChildGraphViewNode mapObject = new ChildGraphViewNode(child);
            childViewMap.put(child.getId() + "", mapObject);
        }

        //third run check if there's cycle in the graph
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final boolean result = detectCycleInGraph(childViewMap.get(String.valueOf(child.getId())));
            if (result) {
                throw new RuntimeException("A CYCLE IS DETECTED BETWEEN VIEWS! PLEASE CHECK YOUR VIEW DEPENDENCY!");
            }
        }

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getId() == -1) {
                ChildGraphViewNode childNode = new ChildGraphViewNode(child);
                childNode.state = State.EXPLORED;
                recursiveOnMeasure(widthMeasureSpec, heightMeasureSpec, childNode, Direction.NONE);
            } else {
                ChildGraphViewNode childNode = childViewMap.get(String.valueOf(getChildAt(i).getId()));
                recursiveOnMeasure(widthMeasureSpec, heightMeasureSpec, childNode, Direction.NONE);
            }
        }

        // After measuring the children, we handle the case if we are wrap content
        int specModeWidth = MeasureSpec.getMode(widthMeasureSpec);
        int specModeHeight = MeasureSpec.getMode(heightMeasureSpec);

        int maxWidth = 0;
        int maxHeight = 0;
        final boolean isWidthWrapContent = specModeWidth == MeasureSpec.AT_MOST;
        final boolean isHeightWrapContent = specModeHeight == MeasureSpec.AT_MOST;
        if (isWidthWrapContent || isHeightWrapContent) {
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (isWidthWrapContent) {
                    maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.getLeftMargin(getMeasuredWidth()) + lp.getRightMargin(getMeasuredWidth()));
                }
                if (isHeightWrapContent) {
                    maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.getTopMargin(getMeasuredHeight()) + lp.getBottomMargin(getMeasuredHeight()));
                }
            }
        }
        if (isWidthWrapContent) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
        }
        if (isHeightWrapContent) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        if (isWidthWrapContent || isHeightWrapContent)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final int childLeft = lp.getChildMarginLeft(child.getMeasuredWidth(), getMeasuredWidth());
                final int childTop = lp.getChildMarginTop(child.getMeasuredHeight(), getMeasuredHeight());
                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(),
                        childTop + child.getMeasuredHeight());
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new DynamicRelativeLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends MarginLayoutParams {
        // All margin should be within -1 to 1
        public float dynamicMarginLeft = 0;
        public float dynamicMarginTop = 0;
        public float dynamicMarginRight = 0;
        public float dynamicMarginBottom = 0;
        public float dynamicWidth = -1;
        public float dynamicHeight = -1;
        public float dynamicWidthMax = -1;
        public float dynamicWidthMin = -1;
        public float dynamicHeightMax = -1;
        public float dynamicHeightMin = -1;

        public int dynamicBaseRefLeft = -1;
        public int dynamicBaseRefTop = -1;
        public int dynamicBaseRefRight = -1;
        public int dynamicBaseRefBottom = -1;

        // gravity for child to align center, center_vertical or center horizontal
        public int gravity = -1;

        // for internal use
        public Integer dynamicRelativeMarginLeft = null;
        public Integer dynamicRelativeMarginTop = null;
        public Integer dynamicRelativeMarginRight = null;
        public Integer dynamicRelativeMarginBottom = null;

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.DynamicRelativeLayout);
            dynamicMarginLeft = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_left, 0);
            dynamicMarginTop = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_top, 0);
            dynamicMarginRight = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_right, 0);
            dynamicMarginBottom = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_bottom, 0);
            dynamicHeight = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_height, -1);
            dynamicWidth = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_width, -1);
            dynamicHeightMax = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_max_height, -1);
            dynamicWidthMax = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_max_width, -1);
            dynamicHeightMin = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_min_height, -1);
            dynamicWidthMin = a.getFloat(R.styleable.DynamicRelativeLayout_layout_dynamic_min_width, -1);
            dynamicBaseRefLeft = a.getResourceId(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_base_left, -1);
            dynamicBaseRefTop = a.getResourceId(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_base_top, -1);
            dynamicBaseRefRight = a.getResourceId(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_base_right, -1);
            dynamicBaseRefBottom = a.getResourceId(R.styleable.DynamicRelativeLayout_layout_dynamic_margin_base_bottom, -1);

            a.recycle();
        }

        public int checkHeightMinMax(int height, int parentHeight) {
            if (dynamicHeightMin > 0 && height < Math.round(dynamicHeightMin * parentHeight)) {
                return Math.round(dynamicHeightMin * parentHeight);
            } else if (dynamicHeightMax > 0 && height > Math.round(dynamicHeightMax * parentHeight)) {
                return Math.round(dynamicHeightMax * parentHeight);
            }

            return height;
        }

        public int checkWidthMinMax(int width, int parentWidth) {
            if (dynamicWidthMin > 0 && width < Math.round(dynamicWidthMin * parentWidth)) {
                return Math.round(dynamicWidthMin * parentWidth);
            } else if (dynamicWidthMax > 0 && height > Math.round(dynamicWidthMax * parentWidth)) {
                return Math.round(dynamicWidthMax * parentWidth);
            }
            return width;
        }

        private boolean isValidPercentRange(float percentage) {
            return (percentage >= 0 && percentage <= 1);
        }

//        public int getCalculatedChildWidth(int childWidth, int parentWidth) {
//            if (childWidth > 0)
//                return childWidth;
////            if (isValidPercentRange(dynamicMarginLeft) && isValidPercentRange(dynamicMarginRight)
////                    && isValidPercentRange(dynamicMarginRight + dynamicMarginLeft))
////                return Math.round(parentWidth * (1 - (dynamicMarginRight + dynamicMarginLeft)));
////            if ((isValidPercentRange(dynamicMarginLeft) && this.width == LayoutParams.MATCH_PARENT) ||
////                    (isValidPercentRange(dynamicMarginLeft) && this.width != LayoutParams.MATCH_PARENT && rightMargin > 0))
////                return parentWidth - rightMargin - Math.round(parentWidth * dynamicMarginLeft);
////            if ((isValidPercentRange(dynamicMarginRight) && this.width == LayoutParams.MATCH_PARENT) ||
////                    (isValidPercentRange(dynamicMarginRight) && this.width != LayoutParams.MATCH_PARENT && leftMargin > 0))
////                return parentWidth - leftMargin - Math.round(parentWidth * dynamicMarginRight);
////            if (leftMargin > 0 && rightMargin > 0)
////                return parentWidth - leftMargin - rightMargin;
//            if (isValidPercentRange(dynamicWidth))
//                return Math.round(parentWidth * dynamicWidth);
//            return childWidth;
//        }

        public int getDynamicWidth(int parentWidth) {
            if (width > 0)
                return checkWidthMinMax(width, parentWidth);
            else if (isValidPercentRange(dynamicWidth))
                return checkWidthMinMax(Math.round(parentWidth * dynamicWidth), parentWidth);
            else
                return width;
        }

        public int getDynamicHeight(int parentHeight) {
            if (height > 0)
                return checkHeightMinMax(height, parentHeight);
            else if (isValidPercentRange(dynamicHeight))
                return checkHeightMinMax(Math.round(parentHeight * dynamicHeight), parentHeight);
            else
                return height;
        }


//        public int getCalculatedChildHeight(int childHeight, int parentHeight) {
//            if (childHeight > 0)
//                return childHeight;
////            if (isValidPercentRange(dynamicMarginTop) && isValidPercentRange(dynamicMarginBottom)
////                    && isValidPercentRange(dynamicMarginBottom + dynamicMarginTop))
////                return Math.round(parentHeight * (1 - (dynamicMarginTop + dynamicMarginBottom)));
////            if ((isValidPercentRange(dynamicMarginTop) && this.height == LayoutParams.MATCH_PARENT) ||
////                    (isValidPercentRange(dynamicMarginTop) && this.height != LayoutParams.MATCH_PARENT && bottomMargin > 0))
////                return parentHeight - bottomMargin - Math.round(parentHeight * dynamicMarginTop);
////            if ((isValidPercentRange(dynamicMarginBottom) && this.height == LayoutParams.MATCH_PARENT) ||
////                    (isValidPercentRange(dynamicMarginBottom) && this.height != LayoutParams.MATCH_PARENT && topMargin > 0))
////                return parentHeight - topMargin - Math.round(parentHeight * dynamicMarginBottom);
////            if (topMargin > 0 && bottomMargin > 0)
////                return parentHeight - topMargin - bottomMargin;
//            if (isValidPercentRange(dynamicHeight))
//                return Math.round(parentHeight * dynamicHeight);
//            return childHeight;
//        }

        public int getChildMarginLeft(int childWidth, int parentWidth) {
            if (dynamicRelativeMarginLeft != null) {
                return dynamicRelativeMarginLeft;
            }

            if (dynamicRelativeMarginRight != null) {
                return dynamicRelativeMarginRight - childWidth;

            }

            if (dynamicMarginLeft != 0) {
                return Math.round(parentWidth * dynamicMarginLeft);
            }

            if (dynamicMarginRight != 0) {
                return parentWidth - childWidth - Math.round(parentWidth * dynamicMarginRight);
            }

            if (leftMargin != 0)
                return leftMargin;

            if (rightMargin != 0)
                return parentWidth - childWidth - rightMargin;

            return 0;

        }

        public int getChildMarginTop(int childHeight, int parentHeight) {
            if (dynamicRelativeMarginTop != null) {
                return dynamicRelativeMarginTop;
            }

            if (dynamicRelativeMarginBottom != null) {
                return dynamicRelativeMarginBottom - childHeight;
            }

            if (dynamicMarginTop != 0) {
                return Math.round(parentHeight * dynamicMarginTop);
            }

            if (dynamicMarginBottom != 0) {
                return parentHeight - childHeight - Math.round(parentHeight * dynamicMarginBottom);
            }
            if (topMargin != 0)
                return topMargin;

            if (bottomMargin != 0)
                return parentHeight - childHeight - bottomMargin;
            return 0;
        }

        public int getLeftMargin(int parentWidth) {
            if (dynamicRelativeMarginLeft != null)
                return dynamicRelativeMarginLeft;
            else if (dynamicMarginLeft == 0 && leftMargin == 0) {
                return 0;
            } else if (dynamicMarginLeft != 0) {
                return Math.round(parentWidth * dynamicMarginLeft);
            } else {
                return leftMargin;
            }
        }

        public int getTopMargin(int parentHeight) {
            if (dynamicRelativeMarginTop != null)
                return dynamicRelativeMarginTop;
            else if (dynamicMarginTop == 0 && topMargin == 0) {
                return 0;
            } else if (dynamicMarginTop != 0) {
                return Math.round(parentHeight * dynamicMarginTop);
            } else {
                return topMargin;
            }
        }


        public int getRightMargin(int parentWidth) {
            if (dynamicRelativeMarginRight != null)
                return dynamicRelativeMarginRight;
            else if (dynamicMarginRight == 0 && rightMargin == 0) {
                return 0;
            } else if (dynamicMarginRight != 0) {
                return Math.round(parentWidth * dynamicMarginRight);
            } else {
                return rightMargin;
            }
        }

        public int getBottomMargin(int parentHeight) {
            if (dynamicRelativeMarginBottom != null) {
                return dynamicRelativeMarginBottom;
            } else if (dynamicMarginBottom == 0 && bottomMargin == 0) {
                return 0;
            } else if (dynamicMarginBottom != 0) {
                return Math.round(parentHeight * dynamicMarginBottom);
            } else {
                return bottomMargin;
            }
        }
    }
}
