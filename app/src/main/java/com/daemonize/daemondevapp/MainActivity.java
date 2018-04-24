package com.daemonize.daemondevapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.daemonize.daemondevapp.imagemovers.BouncingImageTranslationMover;
import com.daemonize.daemondevapp.imagemovers.GravityImageMover;
import com.daemonize.daemondevapp.imagemovers.ImageMover;
import com.daemonize.daemondevapp.imagemovers.ImageMoverDaemon;
import com.daemonize.daemondevapp.imagemovers.ImageTranslationMover;
import com.daemonize.daemondevapp.imagemovers.MainImageTranslationMover;
import com.daemonize.daemonengine.closure.Closure;
import com.daemonize.daemonengine.closure.Return;
import com.daemonize.daemonengine.daemonscroll.DaemonSpell;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.Color.WHITE;

public class MainActivity extends AppCompatActivity {

    private enum Mode {
        GRAVITY,
        CHASE,
        COLLIDE
    }

    private ConstraintLayout layout;

    private Mode mode = Mode.GRAVITY;

    private List<Bitmap> sprite;
    private List<Bitmap> spriteMain;
    private List<Bitmap> bulletSprite;
    private List<Bitmap> explosionSprite;
    private Bitmap grave;

    private List<ImageView> views;
    private List<ImageMoverDaemon> starMovers;

    private ImageMoverDaemon mainMover;
    private ImageView mainView;

    private Bitmap targetImage;
    private ImageView target;

    private int borderX;
    private int borderY;

    private boolean paused = false;

    private TextView textView;

    final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
        public void onLongPress(MotionEvent e) {
            mainMover.setVelocity(0);
            mainMover.setLastCoordinates(
                    e.getX(),
                    e.getY(),
                    binder.bindViewToClosure(mainView)
            );
        }
    });

    private interface EventHandler {
        void handleEvent(MotionEvent event);
    }

    private EventHandler onTouch = new EventHandler() {
        @Override
        public void handleEvent(MotionEvent event) {
            switch (mode) {
                case GRAVITY:
                    if(event.getAction() == MotionEvent.ACTION_DOWN) {

                        target.setX(event.getX());
                        target.setY(event.getY());

                        mainMover.setTouchDirection(event.getX(), event.getY());
                        for(ImageMoverDaemon starMover : starMovers) {
                            starMover.setTouchDirection(event.getX(), event.getY());
                        }
                    }
                    break;
                case CHASE:
                    if (!gestureDetector.onTouchEvent(event) && event.getAction() == MotionEvent.ACTION_DOWN) {
                        target.setX(event.getX());
                        target.setY(event.getY());
                    }
                    break;
                case COLLIDE:
                    if (!gestureDetector.onTouchEvent(event) && event.getAction() == MotionEvent.ACTION_DOWN) {

                        target.setX(event.getX() /*+ (targetImage.getWidth() / 2)*/);
                        target.setY(event.getY() /*+ (targetImage.getHeight()) / 2*/);
                        //mainMover.setTouchDirection(event.getX(), event.getY());
                    }
                    break;
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        onTouch.handleEvent(event);
        return true;
    }

    @FunctionalInterface
    private interface ViewBinder {
        ImageMoveClosure bindViewToClosure(ImageView view);
    }


    public static class ImageMoveClosure implements Closure<ImageMover.PositionedBitmap> {

        protected ImageView view;

        public ImageMoveClosure(ImageView view) {
            this.view = view;
        }

        @Override
        public void onReturn(Return<ImageMover.PositionedBitmap> ret) {

            if (ret.get() == null)
                return;

            ImageMover.PositionedBitmap returnVal = ret.get();
            view.setX(returnVal.positionX);
            view.setY(returnVal.positionY);
            view.setImageBitmap(returnVal.image);
        }
    }

    private class BulletClosure extends ImageMoveClosure {

        private ImageMoverDaemon bulletDaemon;

        public BulletClosure(ImageView view, ImageMoverDaemon bulletdaemon) {
            super(view);
            this.bulletDaemon = bulletdaemon;
        }

        @Override
        public void onReturn(Return<ImageMover.PositionedBitmap> ret) {

            if (
                    ret.get().positionX <= 20
                    || ret.get().positionX >= borderX - 20
                    || ret.get().positionY <= 20
                    || ret.get().positionY >= borderY - 20
                    ) {

                bulletDaemon.stop();
                layout.removeView(view);
                return;
            }

            for (ImageMoverDaemon starMover : starMovers) {
                Pair<Float, Float> starMoverPos = starMover.getLastCoordinates();
                if(Math.abs(ret.get().positionX - starMoverPos.first) <= bulletSprite.get(0).getWidth()
                        && Math.abs(ret.get().positionY - starMoverPos.second) <= bulletSprite.get(0).getHeight()) {

                    bulletDaemon.stop();
                    layout.removeView(view);

                    if (!((ImageTranslationMover) starMover.getPrototype()).isExploading()) {

                        starMover.explode(
                                explosionSprite,
                                binder.bindViewToClosure(((ImageTranslationMover) starMover.getPrototype()).getView()),
                                ret1 -> {
                                    ((ImageTranslationMover) starMover.getPrototype()).getView().setImageBitmap(ret1.get().image);
                                    starMover.stop();
                                }
                        );
                    }
                }
            }

            super.onReturn(ret);
        }
    }

    private ImageView createBulletView() {
        ImageView bulletView = new ImageView(getApplicationContext());
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        bulletView.setLayoutParams(lp);
        layout.addView(bulletView);
        return bulletView;
    }

    private ViewBinder binder = ImageMoveClosure::new;

    private void initViews(List<ImageView> views) {

        views.clear();

        views.add((ImageView) findViewById(R.id.imageView));
        views.add((ImageView) findViewById(R.id.imageView2));
        views.add((ImageView) findViewById(R.id.imageView3));
        views.add((ImageView) findViewById(R.id.imageView4));
        views.add((ImageView) findViewById(R.id.imageView5));

        views.add((ImageView) findViewById(R.id.imageView6));
        views.add((ImageView) findViewById(R.id.imageView7));
        views.add((ImageView) findViewById(R.id.imageView8));
        views.add((ImageView) findViewById(R.id.imageView9));
        views.add((ImageView) findViewById(R.id.imageView10));

        views.add((ImageView) findViewById(R.id.imageView11));
        views.add((ImageView) findViewById(R.id.imageView12));
        views.add((ImageView) findViewById(R.id.imageView13));
        views.add((ImageView) findViewById(R.id.imageView14));
        views.add((ImageView) findViewById(R.id.imageView15));

        views.add((ImageView) findViewById(R.id.imageView16));
        views.add((ImageView) findViewById(R.id.imageView17));
        views.add((ImageView) findViewById(R.id.imageView18));
        views.add((ImageView) findViewById(R.id.imageView19));
        views.add((ImageView) findViewById(R.id.imageView20));

        views.add((ImageView) findViewById(R.id.imageView21));
        views.add((ImageView) findViewById(R.id.imageView22));
        views.add((ImageView) findViewById(R.id.imageView23));
        views.add((ImageView) findViewById(R.id.imageView24));
        views.add((ImageView) findViewById(R.id.imageView25));

        views.add((ImageView) findViewById(R.id.imageView26));
        views.add((ImageView) findViewById(R.id.imageView27));
        views.add((ImageView) findViewById(R.id.imageView28));
        views.add((ImageView) findViewById(R.id.imageView29));
        views.add((ImageView) findViewById(R.id.imageView30));

        views.add((ImageView) findViewById(R.id.imageView31));
        views.add((ImageView) findViewById(R.id.imageView32));
        views.add((ImageView) findViewById(R.id.imageView33));
        views.add((ImageView) findViewById(R.id.imageView34));
        views.add((ImageView) findViewById(R.id.imageView35));

        views.add((ImageView) findViewById(R.id.imageView36));
        views.add((ImageView) findViewById(R.id.imageView37));
        views.add((ImageView) findViewById(R.id.imageView38));
        views.add((ImageView) findViewById(R.id.imageView39));
        views.add((ImageView) findViewById(R.id.imageView40));

    }

    private long bulletCounter;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_D:
                mainMover.setVelocity(new ImageMover.Velocity(20, new ImageMover.Direction(100, 0)));
                return true;
            case KeyEvent.KEYCODE_A:
                mainMover.setVelocity(new ImageMover.Velocity(20, new ImageMover.Direction(- 100, 0)));
                return true;
            case KeyEvent.KEYCODE_W:
                mainMover.setVelocity(new ImageMover.Velocity(20, new ImageMover.Direction(0, - 100)));
                return true;
            case KeyEvent.KEYCODE_S:
                mainMover.setVelocity(new ImageMover.Velocity(20, new ImageMover.Direction(0,  100)));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private class MachineGunSpell implements DaemonSpell {

        private float offset;
        private Pair<Float, Float> initBulletCoord;

        public MachineGunSpell(float offset, Pair<Float, Float> initBulletCoord) {
            this.offset = offset;
            this.initBulletCoord = initBulletCoord;
        }

        @Override
        public void cast() {
            ImageView bulletView;
            ImageMoverDaemon bullet;

            for (int i = 0; i < 3; ++i) {

                bulletView = createBulletView();
                bullet = new ImageMoverDaemon(
                        new ImageTranslationMover(
                                bulletSprite,
                                50,
                                initBulletCoord
                        ).setBorders(borderX, borderY)
                ).setName("Bullet " + Long.toString(++bulletCounter));

                bullet.setVelocity(50);

                bullet.setSideQuest(bullet.moveSideQuest.setClosure(new BulletClosure(bulletView, bullet)));
                bullet.setTouchDirection(
                        target.getX() + (targetImage.getWidth() / 2) + offset,
                        target.getY() + (targetImage.getHeight() / 2) + (float) (Math.pow(-i, i) * 30)
                );

            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainView = findViewById(R.id.imageViewMain);
        views = new ArrayList<>(40);
        starMovers = new ArrayList<>(40);
        initViews(views);


        textView = findViewById(R.id.response);
        textView.setTextColor(WHITE);

        borderX = getResources().getDisplayMetrics().widthPixels - 100;
        borderY = getResources().getDisplayMetrics().heightPixels - 200;

        layout = findViewById(R.id.cl);

        target = createBulletView();

        try {

            targetImage = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("target.png")), 80, 80, false);

            target.getLayoutParams().height = targetImage.getHeight();
            target.getLayoutParams().width = targetImage.getWidth();
            target.requestLayout();

            target.setImageBitmap(targetImage);

            sprite = new ArrayList<>();
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar.png")), 80, 80, false));

            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar90.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar90.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar90.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar90.png")), 80, 80, false));

            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar180.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar180.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar180.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar180.png")), 80, 80, false));

            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar270.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar270.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar270.png")), 80, 80, false));
            sprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstar270.png")), 80, 80, false));

            bulletSprite = new ArrayList<>();
            bulletSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstarRed.png")), 40, 40, false));
            bulletSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstarRed90.png")), 40, 40, false));
            bulletSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstarRed180.png")), 60, 40, false));
            bulletSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("thebarnstarRed270.png")), 40, 40, false));

            explosionSprite = new ArrayList<>();

            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion1.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion2.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion3.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion4.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion5.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion6.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion7.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion8.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion9.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion10.png")), 80, 80, false));

            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion11.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion12.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion13.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion14.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion15.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion16.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion17.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion18.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion19.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion20.png")), 80, 80, false));

            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion21.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion22.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion23.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion24.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion25.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion26.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion27.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion28.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion29.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion30.png")), 80, 80, false));

            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion31.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion32.png")), 80, 80, false));
            explosionSprite.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Explosion33.png")), 80, 80, false));

            grave = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("grave.png")), 80, 80, false);
            explosionSprite.add(grave);

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            spriteMain = new ArrayList<>();

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione30.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione30.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione30.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione30.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione30.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione20.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione10.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione330.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione330.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione330.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione330.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione330.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione340.png")), 150, 150, false));

            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));
            spriteMain.add(Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getAssets().open("Exceptione350.png")), 150, 150, false));

        } catch (IOException e) {
            e.printStackTrace();
        }

        int i = 5;
        for(ImageView view : views) {
            ImageMoverDaemon starMover = new ImageMoverDaemon(
                    new BouncingImageTranslationMover(
                            sprite,
                            i / 20,
                            Pair.create(
                                    (float) borderX % i,
                                    (float) borderY % i
                            )
                    ).setBorders(borderX, borderY).setView(view)
            ).setName("Star " + Integer.toString(i));

            starMover.setSideQuest(starMover.moveSideQuest.setClosure(binder.bindViewToClosure(view)));
            starMover.start();
            starMovers.add(starMover);
            i += 5;
        }

        mainMover = new ImageMoverDaemon(
                    new MainImageTranslationMover(
                            spriteMain,
                            10f,
                            Pair.create(borderX/2f, borderY/2f),
                            starMovers,
                            MainImageTranslationMover.Mode.COLLIDE
                    ).setBorders(borderX, borderY)
        ).setName("Exceptione");


        mainMover.setSideQuest(mainMover.moveSideQuest.setClosure(binder.bindViewToClosure(mainView)));
        mainMover.start();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {

            float offset = (bulletCounter % 3) * 50;
            Pair<Float, Float> lastMainCoord = mainMover.getLastCoordinates();

            MachineGunSpell rafal = new MachineGunSpell(
                    offset,
                    Pair.create(
                            lastMainCoord.first + (spriteMain.get(0).getWidth() / 2) + offset,
                            lastMainCoord.second + (spriteMain.get(0).getHeight() / 2) + offset
                    )
            );

            mainMover.shoot(
                    2,
                    25,
                    binder.bindViewToClosure(mainView),
                    ret -> rafal.cast());
        });


        mode = Mode.COLLIDE;
        Toast.makeText(MainActivity.this, "MODE: COLLIDE", Toast.LENGTH_LONG).show();

//        ExampleDaemon exampleDaemon = new ExampleDaemon(new Example()).setName("ExampleDaemon");
//        exampleDaemon.evenMoreComplicated(
//                        "Constantly updated from another thread: ",
//                        update -> textView.setText(update.get()),
//                        ret -> {
//                            try {
//                                textView.setText(ret.checkAndGet());
//                            } catch (DaemonException e) {
//                                Log.e("DAEMON ERROR", Log.getStackTraceString(e));
//                                textView.setText(e.getMessage());
//                                return;
//                            }
//                            exampleDaemon.evenMoreComplicated(
//                                    "Here we go again: ",
//                                    update -> textView.setText(update.get()),
//                                    ret2 ->  textView.setText(ret2.get())
//                            );
//                        }
//                );


//        new RestClientTestScript(
//                textView,
//                new RestClientDaemon(new RestClient("https://reqres.in"))
//        ).run();

        Toast.makeText(MainActivity.this, "MODE: GRAVITY", Toast.LENGTH_LONG).show();

    }

    @Override
    protected void onPause() {
        super.onPause();
        //exampleDaemon.stop();
        mainMover.stop();
        for(ImageMoverDaemon mover : starMovers) {
            mover.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
