package entities.creatures;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import game2d.Handler;
import gfx.Animation;
import gfx.Assets;

public class Ball extends Creature {

    public static int DefBallWidth = 11, DefBallHeight = 11; //pixels
    //FLIGHT PARAMETERS
    private double Fs = 10 + (Math.random() * 5000); // N, force of the kick that starts the game
    private double forceMax = handler.getParameters().getCustomParameters().get("maxForce");
    //N, maximum force generated by leg muscles during steady shot
    // (player and a ball have no speed before the shot). 2k-4k. 1200N is realistic, but boring.
    //That is NOT a force that player passes to the ball. So, heavier the leg, lighter the shot.
    private double forceMaxTime = 2; //seconds to load maximum value of force (precision vs speed)
    private double w = 0; // Hz, angular velocity of the ball, max 20 ((Carlos-7Hz)
    private double wMax = handler.getParameters().getCustomParameters().get("maxAngular") / 100;
    private double wMaxTime = 0.5; //seconds to load maximum value of w (precision vs speed)
    private double angle = 270; // put degrees, in fact it's in radians
    private double angleMaxTime = 1.5; //seconds to load maximum value of angle (precision vs speed)
    private double mass = 0.44; // kg mass of the ball
    private double ro = handler.getParameters().getCustomParameters().get("airDensity") / 1000; // kg/m^3 air density
    private double R = 0.11; // m ball radius
    private double Cd = 0.3; //0.47drag coefficient (to be replaced with a function of V for each of ball types)
    private double A = 0.5 * Cd * ro * Math.PI * R * R; //coefficient to reduce calculations
    private double B = 16 / 3 * Math.PI * Math.PI * R * R * R * ro; //coefficient to reduce calculations
    private double ratio = handler.getGame().getRatio(); // pitch length is 536 pixels. I want it to be 53meters. So the ratio is 10.
    private double tk = handler.getParameters().getCustomParameters().get("contactTime") / 10000; //s, foot-ball time of contact 8.6-12ms!

    private double Fx = 0; // N, Force of shot
    private double Fy = 0; // N, Force of shot
    private double Fo = 0; //N, Force of air resistance
    private double Fm = 0; //N, Magnus effect
    private double V = 0; // m/s, velocity of the ball
    private double tx = 0, ty = 0; //temporary x,y
    private double tempAngle = 0; //temporary angle
    private double BallFPS = handler.getFPS();
    private double dt = ((Math.round((1 / BallFPS) * 100000000d)) / 100000000d);//Math.round((1/handler.getFPS())*100000d)/100000d; //time increase since last iteration
    boolean flagInit = false;
    boolean flagMovement = true;
    boolean dribbleAllowed = true;
    int caughtBy = 0, rollPeriod = 1000; //0,1,2
    double x0, y0; //initial coordinates of the ball
    private double xPrev1, yPrev1, xPrev2, yPrev2; //coordinates from two previous iterations
    private double currX, currY, prevX, prevY; //coordinates from two previous iterations for rolling calculation
    double dx, dy, ddy, rotationAngle; //coordinates increase since last iteration
    double Z = 0; //distance from x0,y0
    int iteration = 0;
    private boolean scored1, scored2; //scored by player1 or player2
    //private double distance = 0;
    //private double prevV = 0;
    public static double speed, playerAngle;
    public static Animation rollingBall;
    Graphics2D g2d;


    public Ball(Handler handler, BufferedImage icon, int x, int y) {
        super(handler, icon, x, y, DefBallWidth, DefBallHeight);
        rollingBall = new Animation(rollPeriod, Assets.framesBall);
    }

    public void setFs(double val) {
        Fs = val;
    }

    public void initNumbers() {
        V = Fs * tk / mass;
        angle = Math.toRadians(angle);
        y0 = y;
        x0 = x;
        tx = x0 / ratio;
        ty = y0 / ratio;
        xPrev1 = 0;
        yPrev1 = 0;
        xPrev2 = 0;
        yPrev2 = 0;
        flagInit = true; // remember to set as false once shot is done (some def time after release D and if contact ball-layer is true
    }


    public void monitorNumbers() {
        //System.out.println("V =  "+V+", X  "+x+", Y =  "+y+"tempAngle [deg] = "+tempAngle*180/Math.PI+", dx =  "+dx+", dy = "+dy);
        //System.out.println("ball (X,Y): ("+x+";"+y+"), ball (TX, TY): ("+tx+";"+ty+")");
    }

    public void calculatePath() {
        iteration++;

        xPrev1 = tx;
        yPrev1 = ty;

        //ddy = yPrev1 - yPrev2;//-dy;
        dy = (yPrev1 - yPrev2);
        dx = (xPrev1 - xPrev2);

        if (dx == 0 && dy > 0) {
            tempAngle = 0;
        }
        if (dx > 0 && dy > 0) {
            tempAngle = Math.atan(dx / dy);
        }
        if (dx > 0 && dy == 0) {
            tempAngle = 0.5 * Math.PI;
        }
        if (dx > 0 && dy < 0) {
            tempAngle = Math.atan(dx / dy) + Math.PI;
        }
        if (dx == 0 && dy < 0) {
            tempAngle = Math.PI;
        }
        if (dx < 0 && dy < 0) {
            tempAngle = Math.atan(dx / dy) + Math.PI;
        }
        if (dx < 0 && dy == 0) {
            tempAngle = 1.5 * Math.PI;
        }
        if (dx < 0 && dy > 0) {
            tempAngle = Math.atan(dx / dy);
        }

        xPrev2 = xPrev1;
        yPrev2 = yPrev1;

        if (iteration == 1) {//V = Fs*tk/mass;
            tempAngle = angle;
        } else {
            V = (Math.sqrt(dx * dx + dy * dy)) / dt;
        }
        if (V < 0.25 && V > -0.25) {
            flagMovement = false;
            V = 0;
        } //ball stops if V<0.25m/s

        Fo = -A * V * V - 2;//TEST -2, A = 0.5*Cd*ro*Math.PI*R*R
        Fm = B * V * w;//Cd*w*V; //B=16/3*Math.PI*Math.PI*R*R*R*ro
        //w=w-0.0005*V*w; //old way of w dumping calculation.[0.0003-0.0007]
        //new way: w = w0 * e^(-t/7), so to calculate it recursively: w=w*e^(-1/(7*FPS))
        w = w * Math.exp(-1 / (7 * BallFPS));

        if (Math.abs(w) < 0.05) {
            w = 0; //rotation stops if w<0.05 round per s
        }

        Fx = Fo * Math.sin(tempAngle) + Fm * Math.sin(tempAngle + Math.PI / 2);
        Fy = Fo * Math.cos(tempAngle) + Fm * Math.cos(tempAngle + Math.PI / 2);

        tx = tx + (V * Math.sin(tempAngle) * dt + 0.5 * Fx * dt * dt / mass);
        ty = ty + (V * Math.cos(tempAngle) * dt + 0.5 * Fy * dt * dt / mass);

        x = (tx * ratio);
        y = (ty * ratio);
    }

    public void tick() {
        monitorNumbers();
        roll();
        if (!flagInit) {
            initNumbers();
        }
        if (flagMovement) {
            calculatePath();
        }
    }

    private void roll() {
        prevX = currX;
        prevY = currY;
        currX = tx;
        currY = ty;

        if (caughtBy == 0) {
            rollingBall.setPeriod(100 - 20 * V);
            if ((V > 13) || (V == 0)) {
                rollingBall.setPeriod(100000);
            }
            if (V < 13) {
                rotationAngle = -tempAngle - Math.PI;
            }
            if (V >= 13) {
                rotationAngle -= 2 * w * Math.PI / BallFPS;
            }
        }
        if (caughtBy != 0) {
            V = 0;
        }
        if (caughtBy == 1) {
            rollingBall.setPeriod(10000);
            if ((currX != prevX) || (currY != prevY)) {
                rollingBall.setPeriod(100 - 50 * speed);//tu nie dzia?a, trzeba sprawdzi? drybling
            }
            rotationAngle = -playerAngle - Math.PI;
        }

        if (caughtBy == 2) {
            rollingBall.setPeriod(10000);
            if ((currX != prevX) || (currY != prevY)) {
                rollingBall.setPeriod(100 - 50 * speed);//tu nie dzia?a, trzeba sprawdzi? drybling
            }
            rotationAngle = -playerAngle - Math.PI;
        }

        if ((caughtBy == 0) && (V < 0.5)) {
            rollingBall.setPeriod(900000);
        }
        rollingBall.tick();
    }

    @Override
    public void render(Graphics g) {
        g2d = (Graphics2D) g;
        g2d.rotate(rotationAngle, x + DefBallWidth / 2, y + DefBallHeight / 2);
        g2d.drawImage(rollingBall.getCurrentFrame(), (int) (x), (int) (y), width, height, null);
        g2d.rotate(-rotationAngle, x + DefBallWidth / 2, y + DefBallHeight / 2);
    }

    public double getXPrev2() {
        return xPrev2;
    }

    public void setXPrev2(double val) {
        xPrev2 = val;
    }

    public double getYPrev2() {
        return yPrev2;
    }

    public void setYPrev2(double val) {
        yPrev2 = val;
    }

    public double getTX() {
        return tx;
    }

    public void setTX(double val) {
        tx = val;
    }

    public double getTY() {
        return ty;
    }

    public void setTY(double val) {
        ty = val;
    }

    public void setforceMax(double val) {
        forceMax = val;
    }

    public double getforceMax() {
        return forceMax;
    }

    public void setforceMaxTime(double val) {
        forceMaxTime = val;
    }

    public double getforceMaxTime() {
        return forceMaxTime;
    }

    public double getV() {
        return V;
    }

    public void setV(double val) {
        V = val;
    }

    public double getTempAngle() {
        return tempAngle;
    }

    public void setAngle(double val) {
        angle = val;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngleMaxTime(double val) {
        angleMaxTime = val;
    }

    public double getAngleMaxTime() {
        return angleMaxTime;
    }

    public double getW() {
        return w;
    }

    public void setW(double val) {
        w = val;
    }

    public double getwMax() {
        return wMax;
    }

    public void setwMax(double val) {
        wMax = val;
    }

    public double getwMaxTime() {
        return wMaxTime;
    }

    public void setwMaxTime(double val) {
        wMaxTime = val;
    }

    public double getTk() {
        return tk;
    }

    public double getMass() {
        return mass;
    }

    public double getRatio() {
        return ratio;
    }

    public void setCaughtBy(int indicator) {
        caughtBy = indicator;
    }

    public int getCaughtBy() {
        return caughtBy;
    }

    public boolean getScored1() {
        return scored1;
    }

    public void setScored1(boolean val) {
        scored1 = val;
    }

    public boolean getScored2() {
        return scored2;
    }

    public void setScored2(boolean val) {
        scored2 = val;
    }

    public boolean getDribbleAllowed() {
        return dribbleAllowed;
    }

    public void setDribbleAllowed(boolean val) {
        dribbleAllowed = val;
    }

    public void setFlagMovement(boolean flag) {
        flagMovement = flag;
    }


}