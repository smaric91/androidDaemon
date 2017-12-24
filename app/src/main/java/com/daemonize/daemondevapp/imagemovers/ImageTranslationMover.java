package com.daemonize.daemondevapp.imagemovers;;

import android.graphics.Bitmap;
import android.util.Pair;

import java.util.Iterator;
import java.util.List;

public class ImageTranslationMover implements ImageMover {

    protected List<Bitmap> sprite;
    protected Iterator<Bitmap> spriteIterator;
    protected  float initVelocity = 30;
    protected float velocity = initVelocity;

    protected float lastX;
    protected float lastY;

    private boolean paused = false;

    public void pause(){
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
    }

    protected float borderX;
    protected float borderY;

    protected float currentDirX = 80;
    protected float currentDirY = 20;

    public ImageTranslationMover(List<Bitmap> sprite, float velocity, Pair<Float, Float> startingPos) {
        this.sprite = sprite;
        this.initVelocity = velocity;
        this.velocity = initVelocity;
        lastX = startingPos.first;
        lastY = startingPos.second;
        spriteIterator = sprite.iterator();
    }

    protected Bitmap iterateSprite() {
        if(!spriteIterator.hasNext()) {
            spriteIterator = sprite.iterator();
        }
        return spriteIterator.next();
    }

    @Override
    public void setDirection(Direction direction) {
        this.currentDirX = direction.coeficientX;
        this.currentDirY = direction.coeficientY;
    }

    private boolean wasted = false;

    @Override
    public void setTouchDirection(float x, float y) {

        float diffX = x - lastX;
        float diffY = y - lastY;

        if (Math.abs(diffX) < 20 && Math.abs(diffY) < 20) {
            wasted = true;
        } else {
            wasted = false;
        }

        float a;
        boolean signY = diffY >= 0;
        boolean signX = diffX >= 0;

        if (Math.abs(diffY) >= Math.abs(diffX)) {
           a = Math.abs((100*diffX)/diffY);
           float aY =  100 - a;
           setDirection(new Direction(signX ? a : - a, signY ? aY : - aY));
        } else {
            a = Math.abs((100*diffY)/diffX);
            float aX =  100 - a;
            setDirection(new Direction(signX ? aX : -aX, signY ? a : -a));
        }
    }

    @Override
    public void setBorders(float x, float y) {
        this.borderX = x;
        this.borderY = y;
    }

    @Override
    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    @Override
    public void checkCollisionAndBounce(Pair<Float, Float> colliderCoordinates, float velocity, Direction direction) {}

    @Override
    public PositionedBitmap move() {

        PositionedBitmap ret = new PositionedBitmap();

        if (wasted) {
            ret.wasted = true;
        }

        ret.image = iterateSprite();

        //check borders and recalculate
        if (lastX <= 0) {
            currentDirX = - currentDirX;
            lastX = 0;
        } else if (lastX >= borderX) {
            currentDirX = - currentDirX;
            lastX = borderX;
        }

        if(lastY <= 0) {
            currentDirY = - currentDirY;
            lastY = 0;
        } else if( lastY >= borderY) {
            currentDirY = - currentDirY;
            lastY = borderY;
        }

        if (!paused) {
            lastX += velocity * (currentDirX * 0.01f);
            lastY += velocity * (currentDirY * 0.01f);
        }

        ret.positionX = lastX;
        ret.positionY = lastY;


        return ret;
    }
}
