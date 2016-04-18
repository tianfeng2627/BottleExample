package com.example.bottle.ui.view;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.example.bottle.R;

/**
 * 水瓶
 *
 * @author AndroidTian
 */
public class FlowWaterSurfaceView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {

    public static interface IWaterStateListener {
        /**
         * 当初始化水动画完成时
         */
        public void onWaterAnimationEnd();
    }

    private IWaterStateListener mWaterStateListener;

    private static final long SLEEP_TIME = 30;
    private static final long ROTATE_TIME = 300;
    /**
     * 水位降时的时间值
     */
    private static final long WATERLEVEL_TIME = SLEEP_TIME * 30;
    /**
     * 水位升起时的时间值
     */
    private static final long WATERLEVEL_UP_TIME = SLEEP_TIME * 30;
    private long WATERLEVEL_DELAY_TIME = 0;
    /**
     * 水面高度
     */
    private float mBaseLine = 0;
    private float mFullLine;
    private int mBackgroundColor;
    private int mWaterColor;

    private SurfaceHolder mSurfaceHolder;
    private Paint mPaint;
    private DrawRunnable mRunnable;

    /**
     * 重力感应
     */
    private SensorManager mManager;
    private Sensor mSensor;

    /**
     * 数据
     */
    private float[] mPointArray = new float[100];
    private float mWaveLength = 0; // 波长
    private float mMaxAmp = 0; // 最大振幅
    private float mAmp = 0; // 振幅
    private float mSpeed = 10; // 波浪传播速度
    private float mAngle = 0; // 旋转角度
    private float mLocalAngle = 0; // 此时旋转角度
    private long mLastTime = 0; // 取样时间

    /**
     * 控件宽高
     */
    private float mWidth = 0;
    private float mHeight = 0;

    /**
     * 屏幕宽高
     */
    private float mScreenWidth = 0;
    private float mScreenHeight = 0;

    /**
     * 计算参数
     */
    private int mDirection = 0; // 0 为正向 1为反向
    private float mUpOrDownNum;
    private Bitmap mBottleBitmap;
    /**
     * 是否校准
     */
    private boolean mIsCorrect = true;
    /**
     * 是否初始化过
     */
    private boolean mIsInit = false;
    private float mUpNum = (float) 1 / (WATERLEVEL_UP_TIME / SLEEP_TIME);
    private float mPer = 0; // 百分比
    private float mLoaclPerData = -1; // 本地真实数据

    private boolean mOnPause = false;

    private boolean mShowTips = false;

    public FlowWaterSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FlowWaterSurfaceView(Context context) {
        super(context);
        init();
    }

    private void init() {
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
        mManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        mSensor = mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mRunnable = new DrawRunnable();
        mRunnable.running = true;
        // 初始化画笔
        mPaint = new Paint();
        mPaint.setStyle(Style.FILL);
        mPaint.setAntiAlias(true);
        // 获取屏幕宽度
        int[] screen = getScreenWidthAndHeight((Activity) getContext());
        mScreenWidth = screen[0];
        mScreenHeight = screen[1];
        // 初始化资源
        Resources resources = getContext().getResources();
        mBackgroundColor = resources.getColor(R.color.homepage_watercolor_bg);
        mWaterColor = resources.getColor(R.color.homepage_watercolor);
        mMaxAmp = resources.getDimension(R.dimen.homepage_bottle_max_amp);
        mWidth = resources.getDimension(R.dimen.homepage_bottle_width);
        mHeight = resources.getDimension(R.dimen.homepage_bottle_heigh);

        mFullLine = mHeight / 2;
        mWaveLength = mWidth / 5;
        // 计算数组大小
        int count = (int) (mScreenHeight / (mWaveLength * 2));
        mPointArray = new float[count];
        // 设置背景透明
        setZOrderOnTop(true);
        mSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
    }

    /**
     * 获取屏幕宽高
     *
     * @param activity 当没有activity时传空
     * @return
     */
    public int[] getScreenWidthAndHeight(Activity activity) {
        int[] array = new int[2];
        if (activity == null) {
            return new int[]{0, 0};
        }
        // 获取屏幕宽度
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        array[0] = dm.widthPixels;
        array[1] = dm.heightPixels;
        return array;
    }

    /**
     * 设置水的颜色
     *
     * @param color
     */
    public void setWaterColor(int color) {
        mWaterColor = color;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
        mRunnable.running = true;
        mThread = new Thread(mRunnable);
        mThread.start();
    }

    private Thread mThread;

    /**
     * 刷新水的高度(带动画)
     *
     * @param per 百分比
     */
    public void refreshWater(float per) {
        mIsInit = false;
        mPer = 0;
        mAngle = 0;
        mLoaclPerData = getFloatFormate(per, 2);
        mUpOrDownNum = Math.abs((mLoaclPerData >= 0 ? mLoaclPerData : 0) - (mIsInit ? mPer : 1))
                / (WATERLEVEL_TIME / SLEEP_TIME);
    }

    /**
     * 保留小数点后几位
     *
     * @param a
     * @param num
     * @return
     */
    public static float getFloatFormate(float a, int num) {
        double c = Math.pow(10, num);
        float b = (float) (Math.round(a * c)) / (int) c;// (这里的100就是2位小数点,如果要其它位,如4位,这里两个100改成10000)
        return b;
    }

    /**
     * 无动画
     *
     * @param per
     */
    public void setWaterPer(float per) {
        mIsInit = true;
        mPer = mLoaclPerData = per;
    }

    /**
     * 设置是否校准过
     *
     * @param correct
     */
    public void correct(boolean correct) {
        mIsCorrect = correct;
    }

    /**
     * 刷新水平面高度
     */
    private void refreshBaseLine() {
        mBaseLine = mHeight - (mHeight - mFullLine) * mPer;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mManager.unregisterListener(this);
        mRunnable.running = false;
    }

    private class DrawRunnable implements Runnable {

        boolean running = false;
        /**
         * 计数器
         */
        long start = System.currentTimeMillis();

        @Override
        public void run() {
            while (running) {
                try {
                    if (!mOnPause) {
                        synchronized (mSurfaceHolder) {
                            // Lock the canvas for drawing.
                            Canvas canvas = mSurfaceHolder.lockCanvas();
                            if (canvas == null) {
                                continue;
                            }
                            long end = System.currentTimeMillis();
                            if ((end - start) % 2 == 0) {
                                onChange();
                                start = end;
                            }
                            onDrawCanvas(canvas);
                            mSurfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 数值变换
     */
    private void onChange() {
        if (mPointArray.length == 0) {
            return;
        }
        // 计算水平面高度
        mathWaterLevel();
        // 振幅
        mathAmp();
        if (mPer == mLoaclPerData) {
            // 计算旋转角度
            mathAngle();
        }
    }

    /**
     * 计算振幅
     */
    private void mathAmp() {
        if (mAmp > 0) {
            float point = 0;
            if (0 == mDirection) {
                point = mPointArray[0] - mSpeed;
                if (point <= -mWaveLength * 2) {
                    point = point + mWaveLength * 2;
                }
            } else {
                point = mPointArray[0] + mSpeed;
                if (point > 0) {
                    point = point - mWaveLength * 2;
                }
            }
            for (int i = 0; point - mWaveLength * 2 < mWidth; i++) {
                mPointArray[i] = point;
                point = point + mWaveLength * 2;
            }
            mAmp = mAmp - mAmp / SLEEP_TIME;
        }
    }

    /**
     * 计算旋转角度
     */
    private void mathAngle() {
        if (Math.abs(mAngle - mLocalAngle) > 200) {
            if (mAngle > mLocalAngle) {
                mLocalAngle = mLocalAngle + 360;
            } else if (mAngle < mLocalAngle) {
                mAngle = 360 + mAngle;
            }
        }
        if (mAngle > mLocalAngle) {
            mLocalAngle = mLocalAngle + (mAngle - mLocalAngle) / (ROTATE_TIME / SLEEP_TIME);
        } else if (mAngle < mLocalAngle) {
            mLocalAngle = mLocalAngle - (mLocalAngle - mAngle) / (ROTATE_TIME / SLEEP_TIME);
        }
    }

    public void setWaterStateListener(IWaterStateListener listener) {
        mWaterStateListener = listener;
    }

    /**
     * 计算水平面
     */
    private void mathWaterLevel() {
        if (getVisibility() != View.VISIBLE) {
            mPer = 0;
        }
        if (!mIsInit) {
            // 初始化时水面上升
            mPer = mPer + mUpNum;
            if (mPer > 1) {
                mPer = 1;
            }
            if (mPer == 1) {
                WATERLEVEL_DELAY_TIME++;
                if (WATERLEVEL_DELAY_TIME >= 20) {
                    if (mLoaclPerData == 1) {
                        if (mWaterStateListener != null) {
                            mWaterStateListener.onWaterAnimationEnd();
                        }
                    }
                    mIsInit = true;
                }
            }
            mAmp = mMaxAmp;
        } else {
            // 计算水平面 (下降时)
            if (mPer != mLoaclPerData) {
                if (mPer > mLoaclPerData) {
                    if (mPer < 0 && mLoaclPerData < 0) {
                        mPer = mLoaclPerData;
                    }
                    mPer = mPer - mUpOrDownNum;
                    if (mPer <= mLoaclPerData) {
                        mPer = mLoaclPerData;
                        if (mWaterStateListener != null) {
                            mWaterStateListener.onWaterAnimationEnd();
                        }
                    }
                } else {
                    mPer = mPer + mUpOrDownNum;
                    if (mPer >= mLoaclPerData) {
                        mPer = mLoaclPerData;
                        if (mWaterStateListener != null) {
                            mWaterStateListener.onWaterAnimationEnd();
                        }
                    }
                }
                mAmp = mMaxAmp;
            }
        }
        refreshBaseLine();
    }

    /**
     * 绘制波浪
     *
     * @param canvas
     * @param start
     */
    private void draw(Canvas canvas, float start) {
        // 绘制波峰
        Path path = new Path();
        path.moveTo(start, mBaseLine);
        float first_point = getFinishPoint(start);
        path.quadTo(getTopPoint(start), mBaseLine - mAmp, getFinishPoint(start), mBaseLine); // 设置贝塞尔曲线的控制点坐标和终点坐标
        mPaint.setColor(mWaterColor);
        canvas.drawPath(path, mPaint);
        // 绘制波谷
        Path path2 = new Path();
        path2.moveTo(first_point, mBaseLine);
        path2.quadTo(getTopPoint(first_point), mBaseLine + mAmp, getFinishPoint(first_point), mBaseLine);
        mPaint.setColor(mBackgroundColor);
        canvas.drawPath(path2, mPaint);
        canvas.drawLine(first_point, mBaseLine, getFinishPoint(first_point), mBaseLine, mPaint);
    }

    /**
     * 计算顶点
     *
     * @param start
     * @return
     */
    private float getTopPoint(float start) {
        return start + mWaveLength / 2;
    }

    /**
     * 计算终点
     *
     * @param start
     * @return
     */
    private float getFinishPoint(float start) {
        return start + mWaveLength;
    }

    public void setShowTips(boolean show) {
        mShowTips = show;
    }

    public boolean showTips() {
        return mShowTips;
    }

    private void onDrawCanvas(final Canvas canvas) {
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        // 清屏
        canvas.drawColor(mBackgroundColor);
        // 设置圆心旋转
        float pointY = (mHeight) / 2;
        canvas.save();
        canvas.rotate(mLocalAngle, mWidth / 2, pointY);
        mPaint.setColor(mWaterColor);
        // 绘制背景
        canvas.drawRect(new RectF(-(mScreenWidth - mWidth) / 2, mBaseLine, mScreenWidth, mScreenHeight), mPaint);
        // 绘制波浪
        for (int i = 0, size = mPointArray.length; i < size; i++) {
            float start_local = mPointArray[i];
            if (start_local == 0 && 0 != i) {
                break;
            }
            draw(canvas, start_local);
        }
        canvas.restore();
        // 绘制背景瓶子
        if (null == mBottleBitmap || mBottleBitmap.isRecycled()) {
            createBottleBitmap();
        }
        canvas.drawBitmap(mBottleBitmap, 0, 0, null);
        if (mShowTips) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_onclikcbottle_tips);
            canvas.drawBitmap(bitmap, (mWidth - bitmap.getWidth()) / 2, mBottleBitmap.getHeight() - bitmap.getHeight(),
                    mPaint);
        }
        // 绘制文字
        mPaint.setColor(Color.WHITE);
        String perData;
        if (mIsCorrect) {
            float per = 0 > mPer ? 0 : mPer;
            perData = (int) (getFloatFormate(per, 2) * 100) + "%";
            mPaint.setTextSize(mWidth / 3);
        } else {
            perData = getContext().getString(R.string.homepage_water_no);
            mPaint.setTextSize(mWidth / 6);
        }
        canvas.drawText(perData, (mWidth - mPaint.measureText(perData)) / 2, mHeight * 3 / 4, mPaint);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @SuppressWarnings("deprecation")
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mPer == 0 || mPer != mLoaclPerData) {
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            if (Math.abs(y) < 3 && Math.abs(x) < 3) {
                return;
            }
            long time = System.currentTimeMillis();
            if (time - mLastTime > ROTATE_TIME) {
                // 计算方向
                if (x > 0) {
                    mDirection = 0;
                } else {
                    mDirection = 1;
                }
                // 计算旋转角度
                double g = Math.sqrt(x * x + y * y);
                double cos = y / g;
                if (cos > 1) {
                    cos = 1;
                } else if (cos < -1) {
                    cos = -1;
                }
                double rad = Math.acos(cos);
                if (x < 0) {
                    rad = 2 * Math.PI - rad;
                }
                float angle = (float) (180 * rad / Math.PI);
                mLastTime = time;
                // 计算振幅
                float ag = Math.abs(angle % 360 - mAngle % 360);
                if (mAmp < 3 && ag > 20) {
                    mAmp = ag * 20;
                    if (mAmp > mMaxAmp) {
                        mAmp = mMaxAmp;
                    }
                }
                mAngle = angle;
            }
        }
    }

    private void createBottleBitmap() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.homepage_bottle);
        mBottleBitmap = Bitmap.createBitmap((int) mWidth, (int) mHeight, Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postScale(mWidth / bitmap.getWidth(), mHeight / bitmap.getHeight());
        Canvas canvas = new Canvas(mBottleBitmap);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        canvas.drawBitmap(bitmap, matrix, null);
    }

    /**
     * on stop
     */
    public void stop() {
        // 防止做动画的同时线程关闭
        if (mIsCorrect) {
            mPer = 0;
        } else {
            mPer = 1;
        }
        pause();
        mManager.unregisterListener(this);
    }

    public void pause() {
        mOnPause = true;
    }

    public void restore() {
        mOnPause = false;
    }

    /**
     * on resume
     */
    public void resume() {
        restore();
        mManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }
}
