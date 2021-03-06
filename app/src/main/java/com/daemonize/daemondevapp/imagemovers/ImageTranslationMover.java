package com.daemonize.daemondevapp.imagemovers;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.widget.ImageView;

import com.daemonize.daemonengine.closure.Closure;
import com.daemonize.daemonengine.closure.ReturnRunnable;
import com.daemonize.daemonprocessor.annotations.CallingThread;

import java.util.Iterator;
import java.util.List;

public class ImageTranslationMover implements ImageMover {

    protected List<Bitmap> sprite;
    protected Iterator<Bitmap> spriteIterator;
    protected float initVelocity = 20;

    protected volatile Velocity velocity;

    protected volatile float lastX;
    protected volatile float lastY;

    private ImageView view;

    public ImageView getView() {
        return view;
    }

    public ImageTranslationMover setView(ImageView view) {
        this.view = view;
        return this;
    }

    @Override
    public Pair<Float, Float> getLastCoordinates() {
        return Pair.create(lastX, lastY);
    }

    @Override
    public Velocity getVelocity() {
        return velocity;
    }

    @Override
    public PositionedBitmap setLastCoordinates(float lastX, float lastY) {
        this.lastX = lastX;
        this.lastY = lastY;

        PositionedBitmap ret = new PositionedBitmap();
        ret.image = iterateSprite();

        ret.positionX = lastX;
        ret.positionY = lastY;
        
        return ret;
    }

    protected float borderX;
    protected float borderY;

    public ImageTranslationMover(List<Bitmap> sprite, float velocity, Pair<Float, Float> startingPos) {
        this.sprite = sprite;
        this.initVelocity = velocity;
        this.velocity = new Velocity(velocity, new Direction(80, 20));
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
        this.velocity.direction = direction;
    }

    @Override
    public void setVelocity(Velocity velocity) {
        this.velocity = velocity;
    }

    @Override
    public void setDirectionAndMove(float x, float y, float velocityInt) {

        exploading = false;

        float dX = x - lastX;
        float dY = y - lastY;

        float a;
        boolean signY = dY >= 0;
        boolean signX = dX >= 0;
        velocity.intensity = velocityInt;

        if (Math.abs(dY) >= Math.abs(dX)) {
            a = Math.abs((100*dX)/dY);
            float aY =  100 - a;
            velocity.direction = new Direction(signX ? a : - a, signY ? aY : - aY);
        } else {
            a = Math.abs((100*dY)/dX);
            float aX =  100 - a;
            velocity.direction = new Direction(signX ? aX : -aX, signY ? a : -a);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ImageTranslationMover setBorders(float x, float y) {
        this.borderX = x;
        this.borderY = y;
        return this;
    }

    @Override
    public void setVelocity(float velocity) {
        this.velocity.intensity = velocity;
    }

    @Override
    public void checkCollisionAndBounce(
            Pair<Float, Float> colliderCoordinates,
            Velocity velocity
    ) {}

    @Override
    public PositionedBitmap move() {

        PositionedBitmap ret = new PositionedBitmap();
        ret.image = iterateSprite();

        //check borders and recalculate
        if (lastX <= 0) {
            lastX = 0;
        } else if (lastX >= borderX) {
            lastX = borderX;
        }

        if(lastY <= 0) {
            lastY = 0;
        } else if( lastY >= borderY) {
            lastY = borderY;
        }

        lastX += velocity.intensity * (velocity.direction.coeficientX * 0.01f);
        lastY += velocity.intensity * (velocity.direction.coeficientY * 0.01f);


        ret.positionX = lastX;
        ret.positionY = lastY;

        return ret;
    }

    private volatile boolean exploading;

    public boolean isExploading() {
        return exploading;
    }

    @Override
    public PositionedBitmap explode(List<Bitmap> explodeSprite, Closure<PositionedBitmap> update) throws InterruptedException {

        Handler handler = new Handler(Looper.getMainLooper());
        PositionedBitmap updatePB = new PositionedBitmap();
        exploading = true;

        for (Bitmap bmp : explodeSprite) {

            updatePB.image = bmp;
            updatePB.positionX = lastX;
            updatePB.positionY = lastY;
            handler.post(new ReturnRunnable<>(update).setResult(updatePB));
            Thread.sleep(25);
        }

        Thread.sleep(3000);
        updatePB.image = explodeSprite.get(explodeSprite.size() - 1);
        return updatePB;
    }
}

