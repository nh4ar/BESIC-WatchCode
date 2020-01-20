package com.linklab.inertia.besic;

/*
 * Imports needed by the system to function appropriately
 */
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.content.SharedPreferences;
import android.view.SurfaceHolder;
import android.content.Context;
import android.os.Vibrator;
import android.support.wearable.watchface.WatchFaceStyle;
import android.preference.PreferenceManager;
import android.graphics.PorterDuff;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Environment;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.content.Intent;
import android.graphics.Rect;

import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

/**
 * On Android Wear Watch Face is implemented as a service. This is being used by the application to save resources by giving them to the android system to configure.
 * Every Watch Face Service extends from the CanvasWatchFaceService class and provides a com.linklab.inertia.besic.WatchFace Engine.
 */
public class WatchFace extends CanvasWatchFaceService
{
    /**
     * This class is the default engine for the canvas.
     * @return a new instance of the private class to draw the watch face.
     */
    @Override
    public Engine onCreateEngine()
    {
        return new BESIWatchFace();        // Calls the manually created private engine in the class.
    }

    /**
     * This engine is responsible for the Drawing of the watch face and receives events from the system regarding what is needed to run.
     */
    private class BESIWatchFace extends CanvasWatchFaceService.Engine
    {
        private SharedPreferences sharedPreferences;        // Gets a context to the system shared preferences object
        private Map<String, ?> preferenceKeys;      // Creates a map to store key values
        private Vibrator vibrator;      // This is the variable that access the vibrator in the device
        private Calendar calendar;      // The calendar for the time
        private SystemInformation systemInformation;        // Gets a context to the system information class
        private AlarmManager alarmManager;      // Initializes the alarm manager of the class
        private PendingIntent pendingIntent;        // Initializes the pending intents of the class
        private Paint.FontMetrics startBackground, sleepEODEMABackground;      // Sets variables background
        private DataLogger dataLogger, checkEODDate;      // Initializes a datalogger instance
        private Intent alarmIntent, accelerometer, pedometer, heartrate;     // Initializes the intents of the class
        private Timer heartrateTimer;        // Initializes the timer of the application
        private StringBuilder stringBuilder;        // Initializes a string builder variable
        private TextPaint batteryPaint, timePaint, datePaint, startPaint, sleepEODEMAPaint;     // Sets the paint instance for the texts
        private String batteryLevel, currentTime, currentDate, startMessage, sleepEODEMAMessage, data;        // Sets up string variables
        private Rect batteryLevelTextBounds, currentTimeTextBounds, currentDateTextBounds;        // Sets up bounds for items on canvas
        private boolean drawEODEMA, eodemaAlreadyExecuted;      // Sets up all the boolean to be run on the system
        private int batteryLevelPositionX, batteryLevelPositionY,
                currentTimePositionX, currentTimePositionY, currentDatePositionX, currentDatePositionY,
                startX, startY, sleepEODEMAX, sleepEODEMAY, hapticLevel, heartrateInterval;       // Sets up integer variables.

        /**
         * This method is called when the service of the watch face is called for the first time.
         * This overrides the super onCreate method.
         * @param holder is the holder that is needed to create the system.
         */
        @Override
        public void onCreate(SurfaceHolder holder)
        {
            super.onCreate(holder);     // Calls a creation instance

            this.setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this).setAcceptsTapEvents(true).build());        // Sets the watchface to accept user tap event inputs.
            this.vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);      // Get instance of Vibrator from current Context

            this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());        // Gets the preferences from the shared preference object.
            this.preferenceKeys = this.sharedPreferences.getAll();      // Saves all the key values into a map
            this.systemInformation = new SystemInformation();       // Binds the variable to the calls in the class
            this.alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);     // Sets up the alarm manger

            this.startBackground = new Paint.FontMetrics();     // Sets the background of the button
            this.sleepEODEMABackground = new Paint.FontMetrics();       // Sets the background of the sleep/EODEMA button

            this.batteryPaint = new TextPaint();        // Makes a text paint
            this.timePaint = new TextPaint();        // Makes a text paint
            this.datePaint = new TextPaint();        // Makes a text paint
            this.startPaint = new TextPaint();        // Makes a text paint
            this.sleepEODEMAPaint = new TextPaint();        // Makes a text paint

            this.batteryLevelTextBounds = new Rect();        // Makes a text rectangle
            this.currentTimeTextBounds = new Rect();        // Makes a text rectangle
            this.currentDateTextBounds = new Rect();        // Makes a text rectangle

            this.drawEODEMA = false;     // Initializes the boolean as a false value
            this.eodemaAlreadyExecuted = false;       // Initializes the variable

            this.logHeaders();      // Calls the method to log the headers needed for the files
            this.logInitialSettings();      // Calls the method to log all the items in the settings file
            this.scheduleEndOfDaySurvey();      // Calls the method to perform the action
            this.startAllServices();        // Calls the method

            this.setUpDefaultValues();      // Calls the method
            this.setUpDefaultColors();      // Calls the method
            this.startAllServices();        // Calls the method

            this.heartrateInterval = Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("heartrate_interval", ""))) * 1000;
            this.heartrateTimer = new Timer();      // Assigns a new timer
            this.heartrateTimer.scheduleAtFixedRate(new TimerTask()         // Schedules the timer to run once
            {
                /**
                 * The following is called to run
                 */
                @Override
                public void run()
                {
                    if(!isRunning(HeartRate.class))     // Checks if the service is already running, if it is not
                    {
                        startService(heartrate);        // Starts the heart rate service class

                        data = systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "WatchFace Service" + (",") + "Calling to Start the Heart Rate Class";       // Data to be logged by the system
                        dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), data);      // Sets a new datalogger variable
                        dataLogger.saveData("log");      // Saves the data in the mode specified
                    }
                }
            }, 0, this.heartrateInterval);      // Repeats at the specified interval

            this.invalidate();       // Refreshes the screen.
        }

        /**
         * This is the method that draws all the element that is needed on the screen.
         * @param canvas specifies what is being drawn on
         * @param bounds sets the limits of the screen to be drawn on
         */
        @Override
        public void onDraw(Canvas canvas, Rect bounds)
        {
            super.onDraw(canvas, bounds);       // Calls a drawing instance.

            this.setUpDefaultValues();      // Sets up the values on the UI.
            this.setUpDefaultColors();      // Sets up the colors on the UI.
            this.setUpDateAndTime();       // Sets up the time on the UI.
            this.setUpBatteryLevel();      // Sets up the battery values on the UI.
            this.setUpButtons();        // Sets up the buttons on the UI.

            this.clearCanvas(canvas);       // Clears the screen so new values can be drawn.

            canvas.drawText(this.currentDate, this.currentDatePositionX, this.currentDatePositionY, this.datePaint);       // Calls the canvas to draw the date information
            canvas.drawText(this.currentTime, this.currentTimePositionX, this.currentTimePositionY, this.timePaint);       // Calls the canvas to draw the time information
            canvas.drawText(this.batteryLevel, this.batteryLevelPositionX, this.batteryLevelPositionY, this.batteryPaint);      // Calls the canvas to draw the battery information.

            canvas.drawRect(this.startX, this.startY, this.sleepEODEMAX,    // Draws the specified rectangle
                    this.batteryLevelPositionY-this.batteryLevelTextBounds.height()-15, this.startPaint);       // Continued from previous line
            canvas.drawRect(this.sleepEODEMAX, this.sleepEODEMAY, getResources().getDisplayMetrics().widthPixels,       // Draws the specified rectangle
                    this.batteryLevelPositionY-this.batteryLevelTextBounds.height()-15, this.sleepEODEMAPaint);       // Continued from previous line
            this.reconfigureButtons();      // Calls the method
            canvas.drawText(this.startMessage, this.startX+20, this.startY + (this.startY/3) + 12, this.startPaint);      // Calls the canvas to draw the message information
            canvas.drawText(this.sleepEODEMAMessage, this.sleepEODEMAX+8, this.sleepEODEMAY+(this.sleepEODEMAY/3)+8, this.sleepEODEMAPaint);        // Calls the canvas to draw the message
        }

        /**
         * Waits for the screen to be tapped by the user. Then does case analysis to determine the appropriate action needed.
         * @param tapType is the type of tap performed on the screen
         * @param x is the x location on the screen where the tap was performed
         * @param y is the y location on the screen where the tap was performed
         * @param eventTime is how long the type of tap was performed for
         */
        @Override
        public void onTapCommand(@TapType int tapType, int x, int y, long eventTime)
        {
            int startButtonXEnd = (getResources().getDisplayMetrics().widthPixels / 2)+(getResources().getDisplayMetrics().widthPixels / 15);       // The end of the start button x location
            int sleepEODEMAXEnd = getResources().getDisplayMetrics().widthPixels;       // The end position of the sleep button

            int buttonsYEnd = this.batteryLevelPositionY-this.batteryLevelTextBounds.height()-15;       // The end of the start button y location

            switch (tapType)        // Switch case for the tap type
            {
                case WatchFaceService.TAP_TYPE_TOUCH:       // Checks if the tap type was a touch
                    if (x >= startX && x < startButtonXEnd && y >= startY && y <= buttonsYEnd)     // Determines if this was around the start button
                    {
                        this.vibrator.vibrate(hapticLevel);     // Vibrates the system for the specified time

                        Intent surveyIntent = new Intent (WatchFace.this, PainSurvey.class);        // Calls an intent to start a new activity
                        surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);       // Adds a new task for the service to start the activity
                        startActivity(surveyIntent);        // Starts the activity specified
                    }

                    if (x > startButtonXEnd && x < sleepEODEMAXEnd && y >= sleepEODEMAY && y <= buttonsYEnd)
                    {
                        if (drawEODEMA && !this.checkEODDate.readData().contains(this.systemInformation.getDateTime("yyyy/MM/dd")))     // Checks if the daily ema button needs is available
                        {
                            this.vibrator.vibrate(hapticLevel);     // Vibrates the system for the specified time

                            Intent surveyIntent = new Intent (WatchFace.this, EndOfDaySurvey.class);        // Calls an intent to start a new activity
                            surveyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);       // Adds a new task for the service to start the activity
                            startActivity(surveyIntent);        // Starts the activity specified
                        }
                        else        // If the daily EMA button is not needed to show
                        {
                            this.vibrator.vibrate(hapticLevel);     // Vibrates the system for the specified time

                            this.systemInformation.setSleepMode(!this.systemInformation.getSleepMode());     // Sets the sleepMode level to be altered
                            this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + "," + "Watch Face" + "," + "SleepMode Enabled?: "+this.systemInformation.getSleepMode() + ("\n");     // Sets data to be logged by system
                            this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.system), this.data);      // Sets a new datalogger variable
                            this.dataLogger.saveData("log");        // Saves the data

                            this.data = String.valueOf(this.systemInformation.getSleepMode());      // Sets the data to be written
                            this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_information), getResources().getString(R.string.sleepmode), this.data);      // Sets a new datalogger variable
                            this.dataLogger.saveData("write");      // Saves the data in the mode specified

                            this.invalidate();       // Immediately updates the screen
                        }
                    }

                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:        // Checks if the user dismissed the touch
                    break;      // Breaks the tap action
            }
        }

        /**
         * This method sets up buttons on the screen
         */
        private void setUpButtons()
        {
            this.startX = 0;        // Sets the starting x location
            this.sleepEODEMAX = (getResources().getDisplayMetrics().widthPixels / 2)+(getResources().getDisplayMetrics().widthPixels / 15);     // Sets the starting x location

            this.startY = this.currentTimePositionY+(this.currentTimeTextBounds.height()/3)+10;        // Sets the starting y location
            this.sleepEODEMAY = this.currentTimePositionY+(this.currentTimeTextBounds.height()/3)+10;     // Sets the starting y location

            this.startPaint.setTextSize(Integer.valueOf(getResources().getString(R.string.ui_start_button_size)));        // Initializes button size
            this.sleepEODEMAPaint.setTextSize(Integer.valueOf(getResources().getString(R.string.ui_sleep_button_size)));        // Sets the sleep button text size

            this.startPaint.getFontMetrics(this.startBackground);       // Sets background
            this.sleepEODEMAPaint.getFontMetrics(this.sleepEODEMABackground);       // Sets background

            this.startMessage = getResources().getString(R.string.start_string);        // Sets the string of the button
            this.checkEODDate = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_information), getResources().getString(R.string.eodmode), "Checking End Of Day File");        // Makes a new data logger item

            this.drawStartButton();      // Calls the method
            this.decideSleepEODEMAButton();      // Calls the method

            if (drawEODEMA && !this.checkEODDate.readData().contains(this.systemInformation.getDateTime("yyyy/MM/dd")))     // If it is time to draw the end of day EMA
            {
                this.sleepEODEMAMessage = getResources().getString(R.string.eodema_string);      // Sets the string of the button
                this.drawEODEMAButton();      // Calls the method
            }
            else        // If not, draw the sleep automatically
            {
                this.sleepEODEMAMessage = getResources().getString(R.string.sleep_string);      // Sets the string of the button
                this.drawSleepButton();      // Calls the method
            }
        }

        /**
         * This method sets up the Date and Time information needed such as screen location, text, and paint.
         */
        private void setUpDateAndTime()
        {
            this.timePaint.getTextBounds(this.currentTime, 0, this.currentTime.length(), this.currentTimeTextBounds);         // Prints the time information
            this.datePaint.getTextBounds(this.currentDate, 0, this.currentDate.length(), this.currentDateTextBounds);         // Prints the date information

            this.timePaint.setTextSize(Float.valueOf(getResources().getString(R.string.ui_time_size)));     // Sets the size of the UI element
            this.datePaint.setTextSize(Float.valueOf(getResources().getString(R.string.ui_date_size)));     // Sets the size of the UI element

            this.currentTimePositionX = Math.abs((getResources().getDisplayMetrics().widthPixels / 2) - (this.currentTimeTextBounds.width()/2) - 5);        // Sets te x location of the time.
            this.currentDatePositionX = Math.abs((getResources().getDisplayMetrics().widthPixels / 2) - (this.currentDateTextBounds.width()/2));        // Sets te x location of the date.

            this.currentTimePositionY = Math.abs((getResources().getDisplayMetrics().heightPixels / 2) - 15);     // Sets the y location of the time.
            this.currentDatePositionY = Math.abs((getResources().getDisplayMetrics().heightPixels / 2) - ((this.currentDateTextBounds.height()*2) + 20) - 15);     // Sets the y location of the date.
        }

        /**
         * This method is called to log the data that is set in the shared preferences to the device.
         */
        private void logInitialSettings()
        {
            this.stringBuilder = new StringBuilder();       // Initializes the string builder variable

            for(Map.Entry<String,?> preferenceItem : preferenceKeys.entrySet())     // For every key in the map
            {
                this.stringBuilder.append(this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS")).append(",").append(preferenceItem.getKey()).append(",").append(preferenceItem.getValue());     // Appends the data to be logged
                this.stringBuilder.append("\n");        // Appends a new line to the data
            }

            this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.settings), String.valueOf(this.stringBuilder));        // Make a new datalogger inference
            this.dataLogger.saveData("log");        // Type of save to do
        }

        /**
         * This method sets up the battery level information needed such as screen location, text, and paint.
         */
        private void setUpBatteryLevel()
        {
            this.batteryPaint.getTextBounds(this.batteryLevel, 0, this.batteryLevel.length(), this.batteryLevelTextBounds);       // Paints the battery information
            this.batteryPaint.setTextSize(Float.valueOf(getResources().getString(R.string.ui_battery_size)));     // Sets the size of the UI element
            this.batteryLevelPositionX = Math.abs((getResources().getDisplayMetrics().widthPixels / 2) - (this.batteryLevelTextBounds.width()/2));      // Sets the x location of the battery level
            this.batteryLevelPositionY = Math.abs(getResources().getDisplayMetrics().heightPixels) - (this.batteryLevelTextBounds.height()/2 + 10);     // Sets the y location of the battery level
        }

        /**
         * This method initializes the required colors for variables needed in the onDraw method.
         */
        @SuppressWarnings("ALL")        // Suppresses the warnings for this method
        private void setUpDefaultColors()
        {
            if (this.isScreenOn())      // Checks if the system is in Ambient mode
            {
                this.datePaint.setColor(Color.WHITE);       // Sets the color of the date on the UI
                this.timePaint.setColor(Color.WHITE);       // Sets the color of the time on the UI.
                this.batteryPaint.setColor(Color.GREEN);        // Sets the color of the battery level.
                this.startPaint.setColor(Color.GREEN);      // Sets color of the start button to this level
                this.sleepEODEMAPaint.setColor(Color.BLUE);      // Sets color of the button to this level
            }
            else        // If the screen is in ambient mode
            {
                this.datePaint.setColor(Color.DKGRAY);       // Sets the color of the date on the UI
                this.timePaint.setColor(Color.LTGRAY);       // Sets the color of the time on the UI.
                this.batteryPaint.setColor(Color.DKGRAY);        // Sets the color of the battery level.
                this.startPaint.setColor(Color.DKGRAY);      // Sets color of the start button to this level
                this.sleepEODEMAPaint.setColor(Color.DKGRAY);      // Sets color of the button to this level
            }

            if (this.getBatteryLevelInteger() <= Integer.valueOf(this.sharedPreferences.getString("low_battery_alert", "")))        // Checks the battery level
            {
                this.batteryPaint.setColor(Color.RED);        // Sets the color of the battery level.
            }
        }

        /**
         * Resets button variables so text can be drawn using the same resources
         */
        private void reconfigureButtons()
        {
            this.startPaint.setTextSize(Integer.valueOf(getResources().getString(R.string.ui_start_button_size)));      // Sets the text size
            this.checkEODDate = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_information), getResources().getString(R.string.eodmode), "Checking End Of Day File");        // Makes a new data logger item

            if (this.drawEODEMA && this.checkEODDate.readData() != null && !this.checkEODDate.readData().contains(this.systemInformation.getDateTime("yyyy/MM/dd")))     // If it is time to draw the end of day ema button
            {
                this.sleepEODEMAPaint.setTextSize(Integer.valueOf(getResources().getString(R.string.ui_survey_button_size)));    // Sets the text size
            }
            else        // If not, draw the sleep button attribute automatically.
            {
                this.sleepEODEMAPaint.setTextSize(Integer.valueOf(getResources().getString(R.string.ui_sleep_button_size)));    // Sets the text size
            }

            if (this.isScreenOn())       // Checks if the screen is on
            {
                this.startPaint.setColor(Color.WHITE);      // Sets the color
                this.sleepEODEMAPaint.setColor(Color.WHITE);        // Sets the color
            }
            else
            {
                this.startPaint.setColor(Color.BLACK);      // Sets the color
                this.sleepEODEMAPaint.setColor(Color.BLACK);        // Sets the color
            }
        }

        /**
         * This method checks with the system information and decides if the sleep button or the daily ema button should be displayed.
         */
        @SuppressWarnings("ALL")        // Suppresses the warnings for this method
        private void decideSleepEODEMAButton()
        {
            int startHour = Integer.valueOf(this.sharedPreferences.getString("eod_manual_start_hour", ""));     // Gets the start hour from preferences
            int startMinute = Integer.valueOf(this.sharedPreferences.getString("eod_manual_start_minute", ""));     // Gets the start minute from preferences
            int startSecond = Integer.valueOf(this.sharedPreferences.getString("eod_manual_start_second", ""));     // Gets the start second from preferences

            int endHour = Integer.parseInt(this.sharedPreferences.getString("eod_manual_end_hour", ""));         // Gets the end hour from preferences
            int endMinute = Integer.valueOf(this.sharedPreferences.getString("eod_manual_end_minute", ""));     // Gets the end minute from preferences
            int endSecond = Integer.valueOf(this.sharedPreferences.getString("eod_manual_end_second", ""));     // Gets the end second from preferences

            this.drawEODEMA = systemInformation.isTimeBetweenTimes(systemInformation.getDateTime("HH:mm:ss"), startHour, endHour, startMinute, endMinute, startSecond, endSecond);     // Calls the deciding method
        }

        /**
         * Draws the sleep button based on the system conditions
         */
        private void drawSleepButton()
        {
            if (this.isScreenOn())       // Checks if the screen is on on the device
            {
                if (this.systemInformation.getSleepMode())     // Checks if sleep mode on the system is not enabled
                {
                    this.sleepEODEMAPaint.setColor(Color.GRAY);      // Sets color to this level
                }
                else        // if sleep mode is enabled
                {
                    this.sleepEODEMAPaint.setColor(Color.BLUE);      // Sets color to this level
                }
            }
            else        // If the screen is off on the device
            {
                this.sleepEODEMAPaint.setColor(Color.DKGRAY);      // Sets color to this level
            }
        }

        /**
         * This method creates the header files for the directories data is logged to
         */
        private void logHeaders()
        {
            File directory = new File(Environment.getExternalStorageDirectory() + "/" + this.sharedPreferences.getString("directory_key", ""));     // Makes a reference to a directory
            if (!directory.isDirectory())       // Checks if the directory is a directory or not, if not, it runs the following
            {
                String[][] Files =      // A list of file and their headers to be made
                        {
                                {getResources().getString(R.string.subdirectory_information), getResources().getString(R.string.eodmode), "Date"},       // End of day Updater file
                                {getResources().getString(R.string.subdirectory_information), getResources().getString(R.string.sleepmode), String.valueOf(systemInformation.getSleepMode())},      // SleepMode Updater file
                                {getResources().getString(R.string.subdirectory_sensors), getResources().getString(R.string.pedometer), getResources().getString(R.string.pedometer_header)},       // Pedometer file
                                {getResources().getString(R.string.subdirectory_sensors), getResources().getString(R.string.accelerometer), getResources().getString(R.string.accelerometer_header)},      // Accelerometer file
                                {getResources().getString(R.string.subdirectory_sensors), getResources().getString(R.string.heartrate), getResources().getString(R.string.heartrate_header)},       // Heart Rate File
                                {getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.settings), getResources().getString(R.string.settings_header)},        // Settings file
                                {getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.system), getResources().getString(R.string.system_header)},        // System response file
                                {getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), getResources().getString(R.string.sensor_header)},        // Sensor response file
                                {getResources().getString(R.string.subdirectory_survey_activities), getResources().getString(R.string.painactivity), getResources().getString(R.string.painactivity_header)},        // Pain activity file
                                {getResources().getString(R.string.subdirectory_survey_activities), getResources().getString(R.string.followupactivity), getResources().getString(R.string.followupactivity_header)},        // Followup activity file
                                {getResources().getString(R.string.subdirectory_survey_activities), getResources().getString(R.string.endofdayactivity), getResources().getString(R.string.endofdayactivity_header)},        // Followup activity file
                                {getResources().getString(R.string.subdirectory_survey_responses), getResources().getString(R.string.painresponse), getResources().getString(R.string.painresponse_header)},       // Pain response file
                                {getResources().getString(R.string.subdirectory_survey_responses), getResources().getString(R.string.followupresponse), getResources().getString(R.string.followupresponse_header)},        // Followup response file
                                {getResources().getString(R.string.subdirectory_survey_responses), getResources().getString(R.string.endofdayresponse), getResources().getString(R.string.endofdayresponse_header)},        // End of Day response file
                        };

                for (String[] file : Files)     // Foe every file in the files
                {
                    this.dataLogger = new DataLogger(getApplicationContext(), file[0], file[1], file[2]);       // Make a specified data to the file
                    this.dataLogger.saveData("log");        // Save that data in log mode
                }
            }
        }

        /**
         * This method starts all the sensors that is needed to run for the application
         */
        private void startAllServices()
        {
            this.accelerometer = new Intent(getBaseContext(), Accelerometer.class);     // Sets up the intent to start the service
            this.pedometer = new Intent(getBaseContext(), Pedometer.class);     // Sets up the intent to start the service
            this.heartrate = new Intent(getBaseContext(), HeartRate.class);

            if(!isRunning(Accelerometer.class))     // Checks if the service is already running, if it is not
            {
                startService(this.accelerometer);       // Automatically starts the service

                this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "WatchFace Service" + (",") + "Calling to Start the Accelerometer Class";       // Data to be logged by the system
                this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), this.data);      // Sets a new datalogger variable
                this.dataLogger.saveData("log");      // Saves the data in the mode specified
            }

            if(!isRunning(Pedometer.class))     // Checks if the service is already running, if it is not
            {
                startService(this.pedometer);       // Automatically starts the service

                this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "WatchFace Service" + (",") + "Calling to Start the Pedometer Class";       // Data to be logged by the system
                this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), this.data);      // Sets a new datalogger variable
                this.dataLogger.saveData("log");      // Saves the data in the mode specified
            }

//            this.heartrateInterval = Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("heartrate_interval", ""))) * 1000;
//            this.heartrateTimer = new Timer();      // Assigns a new timer
//            this.heartrateTimer.schedule(new TimerTask()         // Schedules the timer to run once
//            {
//                /**
//                 * The following is called to run
//                 */
//                @Override
//                public void run()
//                {
//                    if(!isRunning(HeartRate.class))     // Checks if the service is already running, if it is not
//                    {
//                        startService(heartrate);        // Starts the heart rate service class
//
//                        data = systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "WatchFace Service" + (",") + "Calling to Start the Heart Rate Class";       // Data to be logged by the system
//                        dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), data);      // Sets a new datalogger variable
//                        dataLogger.saveData("log");      // Saves the data in the mode specified
//                    }
//                }
//            }, 0, this.heartrateInterval);      // Repeats at the specified interval
        }

        /**
         * Schedules the timer that runs the end of day survey at the intended time. This method sends a broadcast after the specified time to the
         * AlarmReceiver class allowing the class to decide what to do with the receiver.
         */
        private void scheduleEndOfDaySurvey()
        {
            if (!this.eodemaAlreadyExecuted)       // If this has not run yet
            {
                this.alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);       // Initializes an intent to be run by the system
                this.alarmIntent.putExtra(getResources().getString(R.string.survey_alarm_key), getResources().getString(R.string.endofday_identifier));     // Puts some extra information into the intent service
                this.pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, this.alarmIntent, 0);     // Initializes a pending intent to be run by the alarm manager

                this.calendar = Calendar.getInstance();     // Makes an instance of the calendar
                this.calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("eod_automatic_start_hour", ""))));     // Assigns the hour
                this.calendar.set(Calendar.MINUTE, Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("eod_automatic_start_minute", ""))));        // Assigns the minute
                this.calendar.set(Calendar.SECOND, Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("eod_automatic_start_second", ""))));        // Assigns the seconds

                long startTime = this.calendar.getTimeInMillis();       // Gets the time in milliseconds

                if (System.currentTimeMillis() > startTime)     // If the time we want is passed
                {
                    startTime = startTime + 24*60*60*1000;      // Move it to the next day
                }

                this.alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, startTime, AlarmManager.INTERVAL_DAY, this.pendingIntent);        // Sets the alarm to run in some specified future time

                this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "WatchFace Service" + (",") + "Successfully set up Alarm for End of Day Survey";       // Data to be logged by the system
                this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.system), this.data);      // Sets a new datalogger variable
                this.dataLogger.saveData("log");      // Saves the data in the mode specified

                this.eodemaAlreadyExecuted = true;
            }
        }

        /**
         * Draws the end of day ema button based on the system attributes
         */
        private void drawEODEMAButton()
        {
            if (this.isScreenOn())       // Checks if the screen is on on the device
            {
                this.sleepEODEMAPaint.setColor(Color.RED);      // Sets color to this level
            }
            else        // If not, sets the following
            {
                this.sleepEODEMAPaint.setColor(Color.DKGRAY);      // Sets color to this level
            }
        }

        /**
         * Draws the start button onto the screen.
         */
        private void drawStartButton()
        {
            if (this.isScreenOn())       // If the screen is on
            {
                this.startPaint.setColor(Color.GREEN);      // Sets color to this level
            }
            else        // If screen is off
            {
                this.startPaint.setColor(Color.DKGRAY);     // Sets color to this level
            }
        }

        /**
         * Checks if a given service is currently running or not
         * @param serviceClass is the service class to be checked
         * @return a boolean true or false
         */
        private boolean isRunning(Class<?> serviceClass)        // A general file that checks if a system is running.
        {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);     // Starts the activity manager to check the service called.
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))        // For each service called by the running service.
            {
                if (serviceClass.getName().equals(service.service.getClassName()))      // It checks if it is running.
                {
                    return true;        // Returns true
                }
            }
            return false;       // If not, it returns false.
        }

        /**
         * This method initializes the required values for variables needed in the onDraw method.
         */
        private void setUpDefaultValues()
        {
            this.currentDate = this.systemInformation.getDateTime("MMM d, yyyy");        // Sets up the date from the specific method.
            this.currentTime = this.systemInformation.getDateTime("h:mm a");        // Sets up the time from the specific method.
            this.batteryLevel = this.getBatteryLevelString();      // Sets up the battery level by calling the specified method.
            this.hapticLevel = Integer.valueOf(Objects.requireNonNull(this.sharedPreferences.getString("haptic_level", "")));       // Sets up the vibration level of the system for haptic feedback
        }

        /**
         * Overridden method updates the time every minute
         */
        @Override
        public void onTimeTick()
        {
            super.onTimeTick();     // Calls to superclass
            this.invalidate();       // Redraws the screen
        }

        /**
         * This method gives the string value modified for battery level text.
         * @return the string needed to set up the battery level.
         */
        private String getBatteryLevelString()
        {
            return getResources().getString(R.string.battery_level_string) + " " + this.getBatteryLevelInteger() + "%";      // Sets up the string shown on the canvas for battery level.
        }

        /**
         * This method gives the integer value needed for battery level.
         * @return the integer needed to set up the battery level.
         */
        private int getBatteryLevelInteger()
        {
            return this.systemInformation.getBatteryLevel(getApplicationContext());     // Gets the battery level as an integer from the helper class
        }

        /**
         * This method erases everything on the watchface
         * @param canvas is which face is being erased
         */
        private void clearCanvas(Canvas canvas)
        {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);     // Clears the current face
        }

        /**
         * Checks if the screen is on
         * @return if the screen is on and not in ambient mode
         */
        private boolean isScreenOn()
        {
            return this.isVisible() && !this.isInAmbientMode();       // Checks the ambient mode level and the visibility
        }
    }
}