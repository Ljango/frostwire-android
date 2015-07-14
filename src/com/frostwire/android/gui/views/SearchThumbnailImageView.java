/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchThumbnailImageView extends ImageView {

    private static final Paint paintCircleFill = new Paint();
    private static final Paint paintCircleStroke = new Paint();
    private static final Paint paintShapeFill = new Paint();

    static {
        paintCircleFill.setColor(Color.parseColor("#b0ffffff"));
        paintCircleFill.setStyle(Paint.Style.FILL);
        paintCircleFill.setAntiAlias(true);

        paintCircleStroke.setColor(Color.parseColor("#ff546676"));
        paintCircleStroke.setStrokeWidth(2);
        paintCircleStroke.setStyle(Paint.Style.STROKE);
        paintCircleStroke.setAntiAlias(true);

        paintShapeFill.setColor(Color.parseColor("#ff546676"));
        paintShapeFill.setStyle(Paint.Style.FILL);
        paintShapeFill.setAntiAlias(true);
    }

    private OverlayState state;

    public SearchThumbnailImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.state = OverlayState.NONE;
    }

    public OverlayState getState() {
        return state;
    }

    public void setOverlayState(OverlayState state) {
        this.state = state;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (state == OverlayState.PREVIEW) {
            drawCircle(canvas);
            drawTriangle(canvas);
        }
    }

    private void drawCircle(Canvas canvas) {
        float x = getWidth() / 2.0f;
        float y = getHeight() / 2.0f;
        float r = getHeight() / 6.0f + 2;

        canvas.drawCircle(x, y, r, paintCircleFill);
        canvas.drawCircle(x, y, r, paintCircleStroke);
    }

    private void drawTriangle(Canvas canvas) {
        int x = getWidth() / 2;
        int y = getHeight() / 2;
        int w = getHeight() / 7;
        Path path = getTriangle(new Point(x - w / 2 + 3, y - w / 2), w);
        canvas.drawPath(path, paintShapeFill);
    }

    private Path getTriangle(Point p1, int width) {
        Point p2 = new Point(p1.x, p1.y + width);
        Point p3 = new Point(p1.x + width, p1.y + (width / 2));

        Path path = new Path();
        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);

        return path;
    }

    public enum OverlayState {
        NONE, PREVIEW
    }
}
