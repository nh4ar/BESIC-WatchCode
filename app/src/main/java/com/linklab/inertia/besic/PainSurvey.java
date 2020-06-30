package com.linklab.inertia.besic;

/*
 * Imports needed by the system to function appropriately
 */
import android.annotation.SuppressLint;
import android.support.wearable.activity.WearableActivity;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.content.Context;
import android.os.Vibrator;
import android.view.Window;
import android.preference.PreferenceManager;
import android.app.ActivityManager;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Button;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.TimerTask;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.Date;

/**
 * The logic for the survey in regards to immediate pain. This activity is launched as soon as the start button in the watchface is pressed.
 * This survey only comes up with a button press and is not initiated by any timer or notification.
 */
public class PainSurvey extends WearableActivity
{
    private SharedPreferences sharedPreferences;        // Gets a reference to the shared preferences of the wearable activity
    private LayoutInflater layoutInflater;      // Layout inflater for the activity
    private Vibrator vibrator;      // Gets a link to the system vibrator
    private Window window;      // Gets access to the touch screen of the device
    private int currentQuestion, answersTapped, index, hapticLevel, activityStartLevel, activityRemindLevel, emaReminderInterval, emaDelayInterval, maxReminder, followupTime;       // Initializes various integers to be used by the system
    private int[] userResponseIndex;        // This is the user response index that keeps track of the index response of the user.
    private Button back, next, answer;      // The buttons on the screen
    private Toast toast;        // Makes the toast variable
    private View view;      // Makes the view variable
    private Timer reminderTimer, followupTimer;        // Sets up the timers for the survey
    private TextView question;      // Links to the text shown on the survey screen
    private String role, data, startTime, endTime, duration;        // Sets up all the string variable in the system
    private String[] userResponses, questions;     // String list variables used in the method
    private String[][] answers;     // String list in list variables used in the class
    private Intent followUpEMA, heartRate, estimote;     // Initializes an intent variable
    private DataLogger dataLogger;      // Makes a global variable for the data logger
    private StringBuilder surveyLogs, systemLogs;       // Initializes a global string builder variable
    private SimpleDateFormat timeFormatter;     // Initiates a date time variable
    private SystemInformation systemInformation;        // Gets a reference to the system information class
    private ArrayList<String> responses;    // This is a string that is appended to.


    private final String[] caregiverQuestions =       // These are the questions for the care giver in order.
            {
                    "Is patient having pain now?",
                    "What is patient's pain level?",
                    "How distressed are you?",
                    "How distressed is the patient?",
                    "What is your current location?",
                    "Did patient take an opioid for the pain?",
                    "Why not?",
                    "Did patient do anything else for the pain?",
                    "What did patient do?",
                    "Ready to submit your answers?",
            };
    private final String[][] caregiverAnswers =       // These are the answers for the care giver in order.
            {
                    {"Yes", "No"},
                    {"1","2","3","4","5","6","7","8","9","10"},
                    {"Not at all", "A little", "Fairly", "Very"},
                    {"Not at all", "A little", "Fairly", "Very", "Unsure"},
                    {"Living Room", "Bedroom", "Kitchen", "Outside the home", "Other"},
                    {"Yes", "No", "Unsure"},
                    {"Not time yet", "Side effects", "Out of pills", "Worried taking too many", "Pain not bad enough", "Other Reason", "Unsure"},
                    {"Yes", "No", "Unsure"},
                    {"Exercise/Stretching", "Prayer/Meditation", "Hot/Cold Pack", "Position Change", "Rest", "Massage", "Took Non-Opioid Medication",  "Other"},
                    {"Yes", "No"},
            };

    private final String[] patientQuestions =         // These are the patient questions in order.
            {
                    "Are you in pain now?",
                    "What is your pain level?",
                    "How distressed are you?",
                    "How distressed is your caregiver?",
                    "What is your current location?",
                    "Did you take an opioid for the pain?",
                    "Why not?",
                    "Did you do anything else for the pain?",
                    "What did you do?",
                    "Ready to submit your answers?",
            };
    private final String[][] patientAnswers =         // These are the patient answers in order.
            {
                    {"Yes", "No"},
                    {"1","2","3","4","5","6","7","8","9","10"},
                    {"Not at all", "A little", "Fairly", "Very"},
                    {"Not at all", "A little", "Fairly", "Very", "Unsure"},
                    {"Living Room", "Bedroom", "Kitchen", "Outside the home", "Other"},
                    {"Yes", "No"},
                    {"Not time yet", "Side effects", "Out of pills", "Worried taking too many", "Pain not bad enough", "Other Reason"},
                    {"Yes", "No"},
                    {"Exercise/Stretching", "Prayer/Meditation", "Hot/Cold Pack", "Position Change", "Rest", "Massage", "Took Non-Opioid Medication",  "Other"},
                    {"Yes", "No"}
            };

    /**
     * This method is responsible for setting up the global items for tha activity in order to function properly.
     * @param savedInstance an instance of the activity from the superclass
     */
    @Override
    protected void onCreate(Bundle savedInstance)
    {
        super.onCreate(savedInstance);      // Makes a call to the super class method

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());        // Gets a reference to the shared preferences of the activity
        this.systemInformation = new SystemInformation();       // Gets a reference to the system information of the wearable activity

        this.vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);      // Initializes the vibrator variable

        this.startTime = this.getEstablishedTime();    // Sets the start time of the survey
        this.systemLogs = new StringBuilder(this.startTime).append(",").append("Pain Survey").append(",").append("Starting Pain Survey").append("\n");       // Logs to the string builder variable

        this.unlockScreen();        // Calls the method to unlock the screen in a specified manner
        this.setContentView(R.layout.activity_ema);      // Sets the view of the watch to be the specified activity
        this.decideRoleQuestions();      // Decides the role the device is playing

        this.userResponses = new String[questions.length];      // Sets up the responses needed by the user to be the length of the number given
        this.userResponseIndex = new int[userResponses.length];     // Sets up the index to be the integer value of the user responses length
        this.responses = new ArrayList<>();     // Initializes the array list of the responses by the user
        this.reminderTimer = new Timer();       // Sets up the variable as a new timer for the instance of this class
        this.toast = new Toast(getApplicationContext());      // Sets up the toast in term of this context
        this.heartRate = new Intent(getApplicationContext(), HeartRate.class);     // Makes an intent to the heartrate class
        this.estimote = new Intent(getApplicationContext(), Estimote.class);       // Makes an intent to the estimote class
        this.followUpEMA = new Intent(getApplicationContext(), FollowupSurvey.class);       // Makes an intent to the estimote class
        this.currentQuestion = 0;       // Sets the number of questioned answered by the user

        this.back = findViewById(R.id.back);        // Gets a reference to the back button
        this.next = findViewById(R.id.next);        // Gets a reference to the next button
        this.answer = findViewById(R.id.responses);        // Gets a reference to the answer button
        this.question = findViewById(R.id.request);        // Gets a reference to the question text view

        this.hapticLevel = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("haptic_level", "")));       // Sets up the vibration level of the system for haptic feedback
        this.activityStartLevel = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("activity_start", ""))) * 1000;      // Alert for starting the activity
        this.activityRemindLevel = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("activity_remind", ""))) * 1000;      // Alert for starting the activity
        this.emaReminderInterval = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("pain_remind_interval", ""))) * 1000;       // Alert to continue survey initialized
        this.emaDelayInterval = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("pain_remind", ""))) * 1000;       // Amount to offset reminder alert by
        this.followupTime = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("followup_trigger", ""))) * 1000;       // Followup ema timer extension
        this.maxReminder = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("pain_remind_max", "")));        // Maximum reminders allowed for the survey

        this.vibrator.vibrate(this.activityStartLevel);     // Vibrates the watch to signify the start of an activity
        this.scheduleReminderTimer();       // Calls the method to schedule the timer for the survey
        this.setAmbientEnabled();        // Sets ambient mode ability
        this.setAutoResumeEnabled(true);     // Resumes the activity if not killed properly
        this.deploySurvey();        // Calls on the method for the survey to begin
    }

    /**
     * This method runs the survey as intended by the research team. The logic in terms of moving forward, going back, and changing the approach and movements through the
     * questions are all done in this method in as little steps as possible. This method is deployed as soon as the creation of the activity is complete
     */
    private void deploySurvey()
    {
        if(!isRunning(HeartRate.class) || !isRunning(Estimote.class))     // Checks if the classes are running
        {
            this.startService(this.heartRate);       // Starts the service
            this.startService(this.estimote);        // Starts the service
        }

        this.question.setText(questions[this.currentQuestion]);     // Sets the question to be asked to be the current question position
        this.answersTapped = this.userResponseIndex[this.currentQuestion];      // Sets up the index of the answer tapped to be the response index of the current question
        this.responses.clear();     // Cleats the array list of any values in it
        this.maxReminder = Integer.parseInt(Objects.requireNonNull(this.sharedPreferences.getString("pain_remind_max", "")));        // Maximum reminders allowed for the survey

        Collections.addAll(this.responses, this.answers[this.currentQuestion]);     // Calls on the collections object to add all the values in the array list so it can remember them
        this.nextAnswer();      // Calls on the method to update the answer view

        if (this.currentQuestion < questions.length)        // Checks to make sure there is still questions to be asked
        {
            if (this.currentQuestion == 1)     // Checks the question
            {
                this.answer.setTextSize(40);        // Sets the text size
            }
            else if (this.currentQuestion == questions.length-1)        //  Checks to see if the question is the last question
            {
                this.next.setText(this.getResources().getString(R.string.done_button));     // Sets the next button accordingly
            }
            else        // If this is just any other question
            {
                this.next.setText(getResources().getString(R.string.next_button));      // Sets the next text back to the original value
                this.back.setText(getResources().getString(R.string.back_button));      // Sets the back text to the original value
                this.answer.setTextSize(18);        // Sets the text size
            }

            this.next.setOnClickListener(new View.OnClickListener()         // Listens for the button to be clicked
            {
                @Override
                public void onClick(View v)         // When the button is clicked
                {
                    systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append(next.getText().toString()).append(" is clicked").append("\n");       // Logs to the system logs
                    vibrator.vibrate(hapticLevel);      // Vibrates the system for the desired time

                    if (currentQuestion == 0)       // Checks if this is the first question
                    {
                        if (answer.getText().toString().contentEquals(answers[currentQuestion][0]))         // Checks the answer choice
                        {
                            runServices();      // Calls the method to run some services

                            data = systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "Pain Survey" + (",") + "Started HeartRate and Estimote Class";       // Data to be logged by the system
                            dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), data);      // Sets a new datalogger variable
                            dataLogger.saveData("log");      // Saves the data in the mode specified

                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion++;      // Increments the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][1]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            submitSurvey();     // Calls the method to run
                        }
                    }
                    else if (currentQuestion == 5)      // Checks the question location
                    {
                        if (answer.getText().toString().contentEquals(answers[currentQuestion][0]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            userResponses[currentQuestion+1] = "**Skipped**";     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            currentQuestion += 2;       // Skips a question not pertaining to the survey
                            deploySurvey();       // Calls the question system method
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][1]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion++;      // Increments the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][2]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            userResponses[currentQuestion+1] = "**Skipped**";     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            currentQuestion += 2;       // Skips a question not pertaining to the survey
                            deploySurvey();       // Calls the question system method
                        }
                    }
                    else if (currentQuestion == 7)      // Checks the question location
                    {
                        if (answer.getText().toString().contentEquals(answers[currentQuestion][0]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion++;      // Increments the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][1]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            userResponses[currentQuestion+1] = "**Skipped**";     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            currentQuestion += 2;       // Skips a question not pertaining to the survey
                            deploySurvey();       // Calls the question system method
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][2]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            userResponses[currentQuestion+1] = "**Skipped**";     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            currentQuestion += 2;       // Skips a question not pertaining to the survey
                            deploySurvey();       // Calls the question system method
                        }
                    }
                    else if (currentQuestion == questions.length-1)      // Checks if this is the last question in the survey
                    {
                        if (answer.getText().toString().contentEquals(answers[currentQuestion][0]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            logActivity();      // Calls the method to log the data

                            submitSurvey();     // Calls the method to run
                        }
                        else if (answer.getText().toString().contentEquals(answers[currentQuestion][1]))         // Checks the answer choice
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion = 0;      // Resets the current question location
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                    }
                    else        // If it fails all the other checks
                    {
                        userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                        userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                        logActivity();      // Calls the method to log the data

                        currentQuestion++;      // Increments the current question position
                        deploySurvey();     // Calls the method on itself to move the question forward
                    }
                }
            });

            this.back.setOnClickListener(new View.OnClickListener()         // Listens for the button to be clicked
            {
                @Override
                public void onClick(View v)         // When the button is clicked
                {
                    systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append(back.getText().toString()).append(" is clicked").append("\n");       // Logs to the system logs
                    vibrator.vibrate(hapticLevel);      // Vibrates the system for the desired time

                    if (currentQuestion == 0)      // Checks if this is the last question in the survey
                    {
                        userResponses[currentQuestion] = back.getText().toString();     // Adds the data to be saved to an array list
                        userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                        logActivity();      // Calls the method to log the data

                        submitSurvey();     // Calls the method to run
                    }
                    else if(currentQuestion == 7)       // Checks the question number
                    {
                        if(userResponses[currentQuestion-1].contentEquals("**Skipped**"))       // Checks if the question was skipped
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion -= 2;      // Decrements the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                        else        // If the previous question was not skipped
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion--;      // Decrements the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                    }
                    else if (currentQuestion == questions.length-1)      // Checks if this is the last question in the survey
                    {
                        if(userResponses[currentQuestion-1].contentEquals("**Skipped**"))       // Checks if the question was skipped
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion -= 2;      // Decrements the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                        else        // If the previous question was not skipped
                        {
                            userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                            userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                            logActivity();      // Calls the method to log the data

                            currentQuestion--;      // Decrements the current question position
                            deploySurvey();     // Calls the method on itself to move the question forward
                        }
                    }
                    else        // If none of the requirements are fulfilled
                    {
                        userResponses[currentQuestion] = answer.getText().toString();     // Adds the data to be saved to an array list
                        userResponseIndex[currentQuestion] = nextAnswer();      // Sets up the index so that it can always remember the answer
                        logActivity();      // Calls the method to log the data

                        currentQuestion--;      // Decrements the current question position
                        deploySurvey();     // Calls the method on itself to move the question forward
                    }
                }
            });

            this.answer.setOnClickListener(new View.OnClickListener()       // Sets a listener for the button
            {
                @Override
                public void onClick(View v)     // When the button is clicked
                {
                    vibrator.vibrate(hapticLevel);      // Vibrates the system for the desired time
                    systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Answer Choice Toggled Forward").append("\n");       // Logs to the system logs

                    answersTapped += 1;         // Increments the tap on the answer by the specified amount
                    nextAnswer();        // Calls on the method to update the answer view
                }
            });
        }
        else        // If this is not a survey question
        {
            this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Submitting Survey").append("\n");       // Logs to the system logs
            this.submitSurvey();     // Automatically submits the survey
        }
    }

    /**
     * This Method figures out how long it took for the user to complete the survey.
     * @return a string containing the amount of time it took for the survey to be completed
     */
    @SuppressLint("SimpleDateFormat")       // Removes a warning from the date time format
    private String emaDuration()      // This is the duration of the EMA
    {
        this.timeFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");       // This is the format that the times given for comparison will be in.

        try     // The system tires the following.
        {
            Date startTime = timeFormatter.parse(this.startTime);     // Sets the start time to the start time
            Date stopTime = timeFormatter.parse(this.endTime);     // Sets the stop time to be the immediate time
            assert stopTime != null;        // Asserts a not null statement
            assert startTime != null;       // Asserts a not null statement
            long EMADuration = stopTime.getTime() - startTime.getTime();        // Gets the difference between both times
            String EMADurationHours = String.valueOf(EMADuration / (60 * 60 * 1000) % 24);      // Sets the hour difference to the variable
            String EMADurationMinutes = String.valueOf(EMADuration / (60 * 1000) % 60);     // Sets the minutes difference to the variable
            String EMADurationSeconds = String.valueOf((EMADuration / 1000) % 60);      // Sets the seconds difference to the variable
            this.duration = EMADurationHours + ":" + EMADurationMinutes + ":" + EMADurationSeconds;       // Sets the duration to the variable
        }
        catch (ParseException e)        // If an error occurs in the process
        {
            this.duration = "Error, Please Consult the Pain EMA Activities File for the EMA Duration";      // This is the time between the EMAs
        }

        return this.duration;     // Returns the duration time as a string
    }

    /**
     * This method automatically starts the heartrate and the localization sensor if the user decides to go along with performing the survey.
     */
    private void runServices()
    {
        if(isRunning(HeartRate.class) || isRunning(Estimote.class))     // Checks if the classes are running
        {
            this.stopService(this.heartRate);       // Stops the service
            this.stopService(this.estimote);        // Stops the service
        }

        this.startService(this.heartRate);      // Starts the service
        this.startService(this.estimote);       // Starts the service

        this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "Pain Survey" + (",") + "Starting HeartRate and Estimote Class";       // Data to be logged by the system
        this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), this.data);      // Sets a new datalogger variable
        this.dataLogger.saveData("log");      // Saves the data in the mode specified
    }

    /**
     * This method aggregates all the values of the responses into a single variable and logs them all into a file with a specific format.
     * Upon completing the logs, it finishes the survey and initiates a timer for a followup if it is needed.
     */
    private void submitSurvey()
    {
        this.endTime = this.getEstablishedTime();     // Sets the end time of the survey
        this.scheduleFollowupSurvey();      // Calls the method to perform the actions specified
        this.logResponse();     // Calls the method to perform an action
        this.imageToast();       // Shows a specially made toast to the screen
        this.finish();       // Finishes the survey and cleans up the system
    }

    /**
     * Schedules the timer that runs the followup survey at the intended time. This method sends a broadcast after the specified time to the
     * AlarmReceiver class allowing the class to decide what to do with the receiver.
     */
    private void scheduleFollowupSurvey()
    {
        if((this.userResponses[5] != null && this.userResponses[5].equalsIgnoreCase(this.answers[5][0])) || (this.userResponses[7] != null && this.userResponses[7].equalsIgnoreCase(this.answers[7][0])))     // Checks for a specific requirement
        {
            try     // Tries the following
            {
                this.followupTimer.cancel();        // Cancels the timer if already running
                this.followupTimer.purge();     // Clears the timer
            }
            catch (Exception e)     // If anything goes wrong
            {
                // Do nothing
            }
            finally         // After everything is taken care of
            {
                this.followupTimer = new Timer();       // Makes a new timer
                this.followupTimer.schedule(new TimerTask()         // Schedules the HRTimer at a fixed rate
                {
                    /**
                     * The following is called to run
                     */
                    @Override
                    public void run()
                    {
                        startActivity(followUpEMA);     // Calls to start the followup EMA

                        data = systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "Pain Survey" + (",") + "Starting Followup EMA";       // Data to be logged by the system
                        dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), data);      // Sets a new datalogger variable
                        dataLogger.saveData("log");      // Saves the data in the mode specified
                    }
                }, this.followupTime);      // Repeats at the specified interval

                this.systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Followup EMA Scheduled").append("\n");       // Logs to the system logs
            }
        }
        else        // If the requirement was failed
        {
            this.systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Followup EMA NOT Scheduled").append("\n");       // Logs to the system logs
        }

        if(isRunning(HeartRate.class) || isRunning(Estimote.class))     // Checks if the classes are running
        {
            this.stopService(this.heartRate);       // Stops the service
            this.stopService(this.estimote);        // Stops the service

            this.data = this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + (",") + "Pain Survey" + (",") + "Stopped HeartRate and Estimote Class";       // Data to be logged by the system
            this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.sensors), this.data);      // Sets a new datalogger variable
            this.dataLogger.saveData("log");      // Saves the data in the mode specified
        }
    }

    /**
     * This method decides the role that the watch is playing in terms of patient or caregiver and then based on the decision makes the questions and answers
     * used in the survey to be parallel with what is needed by the user.
     */
    private void decideRoleQuestions()
    {
        this.role = this.sharedPreferences.getString("user_info", "");      // Sets the role of the device based on the preferences setting

        assert this.role != null;       // Makes sure the role is not null
        if(this.role.equalsIgnoreCase("PT"))        // Checks the role value against a patient identifier
        {
            this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Device is set as Patient").append("\n");       // Logs to the system logs
            this.questions = this.patientQuestions;     // Sets the questions to be that of the patient
            this.answers = this.patientAnswers;     // Sets the answers to be that of the patient
        }
        else if (this.role.equalsIgnoreCase("CG"))      // Checks the role value against a caregiver identifier
        {
            this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Device is set as Caregiver").append("\n");       // Logs to the system logs
            this.questions = this.caregiverQuestions;       // Sets the questions to be that of the patient
            this.answers = this.caregiverAnswers;       // Sets the answers to be that of the caregiver
        }
    }

    /**
     * Sets up and runs the timer that reminds the user to complete the survey if not completed after a specified amount of time.
     * This timer will run for a specified amount of time before automatically submitting the survey and ending.
     */
    private void scheduleReminderTimer()
    {
        this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Scheduling Reminder Timer").append("\n");       // Logs to the system logs

        this.reminderTimer.scheduleAtFixedRate(new TimerTask()         // Sets up a new timer task for this survey
        {
            @Override
            public void run()           // When the timer is called to run
            {
                systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Reminding User to Complete Survey").append("\n");       // Logs to the system logs

                if (maxReminder == 0)       // Checks if the max amount has been reached
                {
                    new Handler(Looper.getMainLooper()).post(new Runnable()     // Gets a lopper to find the main thread of the application
                    {
                        @Override
                        public void run()       // Runs the following on the main thread
                        {
                            systemLogs.append(getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Automatically Submitting Survey").append("\n");       // Logs to the system logs

                            submitSurvey();     // Calls the method to automatically submit the survey
                        }
                    });
                }

                vibrator.vibrate(activityRemindLevel);      // Vibrates the device for a specified amount of time
                maxReminder--;      // Reduces the reminder to a certain value '0'
            }
        }, this.emaDelayInterval, this.emaReminderInterval);        // Sets up the delay offset and how often to run the logic in the timer
    }

    /**
     * Sets up the only the responses to the answers and logs them to a specified file given
     */
    private void logResponse()
    {
        this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Logging Survey Activity").append("\n");       // Logs to the system logs
        this.surveyLogs = new StringBuilder(systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + "," + getResources().getString(R.string.painsurvey_name));     // Starts to log the data

        for (String userResponse : userResponses)       // Checks every response in the responses
        {
            this.surveyLogs.append(",").append(userResponse);        // Appends every answer to a string builder variable
        }
        this.surveyLogs.append(",").append(this.emaDuration());      // Appends the duration of the survey to the end of the string builder

        this.systemLogs.append(this.getEstablishedTime()).append(",").append("Pain Survey").append(",").append("Pain Survey Finished and Submitted").append("\n");       // Logs to the system logs

        this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_survey_responses), getResources().getString(R.string.painresponse), String.valueOf(this.surveyLogs));        // Makes a new data logger item
        this.dataLogger.saveData("log");        // Saves the data in the format given

        this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_logs), getResources().getString(R.string.system), String.valueOf(this.systemLogs));        // Makes a new data logger item
        this.dataLogger.saveData("log");        // Saves the data in the format specified
    }

    /**
     * This method sets up the image toast that is to be run
     */
    private void imageToast()       // This is the image toast
    {
        this.layoutInflater = this.getLayoutInflater();      // Calls a layout
        this.view = layoutInflater.inflate(R.layout.toast_0, (ViewGroup) findViewById(R.id.relativeLayout));     // Sets the layout to the view
        this.toast.setDuration(Toast.LENGTH_LONG);       // Makes the toast longer
        this.toast.setView(this.view);        // Sets the view
        this.toast.show();       // Shows the toast
    }

    /**
     * Checks if a given service is currently running or not
     * @param serviceClass is the service class to be checked
     * @return a boolean true or false
     */
    @SuppressWarnings("ALL")        // Suppresses the warnings associated with this method
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
     * Sets up the information to be saved by the activity and actions happening in the activity
     */
    private void logActivity()
    {
        this.data =  this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS") + "," + getResources().getString(R.string.painsurvey_name) + "," +       // Data to be saved by the device
                this.currentQuestion + "," + userResponses[currentQuestion] + "," + this.index;       // Data to save
        this.dataLogger = new DataLogger(getApplicationContext(), getResources().getString(R.string.subdirectory_survey_activities), getResources().getString(R.string.painactivity), this.data);      // Sets up data save path and information.
        this.dataLogger.saveData("log");      // Logs the data into the directory specified.
    }

    /**
     * This method sets up the screen actions that go along with waking up to the activity and how the screen behaves while the activity is ongoing
     */
    private void unlockScreen()
    {
        this.window = this.getWindow();     // Gets access to the screen of the device
        this.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);      // Makes sure the device can wake up if locked
        this.window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);        // Makes sure the screen is on if off
        this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        // Makes sure the screen stays on for the duration of the activity
    }

    /**
     * This method gets the next answer in the list of answers given. If the answer is the last answer, it goes back to the start of the list
     * @return ths index that the user is using as the answer
     */
    private int nextAnswer()
    {
        this.index = this.answersTapped%this.responses.size();      // Sets up the index that the user is currently on
        this.answer.setText(this.responses.get(this.index));        // Sets the answer choice seen by the user to be that of the index in the answer choice
        return this.index;       // Returns the index to where the method was called
    }

    /**
     * Makes it easier to get the time in the predetermined format
     * @return the date and time
     */
    private String getEstablishedTime()
    {
        return this.systemInformation.getDateTime("yyyy/MM/dd HH:mm:ss:SSS");       // Returns the time in this format
    }

    /**
     * This method is called to clean up the method by removing all floating variables and timers that are not needed anymore
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();      // Calls the super class method

        this.reminderTimer.cancel();        // Cancels the timer that is running
    }
}
