package com.pathplanner.lib;

import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import com.pathplanner.lib.PathPlannerTrajectory.EventMarker;
import com.pathplanner.lib.PathPlannerTrajectory.Waypoint;

public class PathPlanner {
    protected static double resolution = 0.004;

    /**
     * Load a path file from storage
     * @param name The name of the path to load
     * @param constraints Max velocity and acceleration constraints of the path
     * @param reversed Should the robot follow the path reversed
     * @return The generated path
     */
    public static PathPlannerTrajectory loadPath(String name, PathConstraints constraints, boolean reversed) {
        try(BufferedReader br = new BufferedReader(new FileReader(new File(Filesystem.getDeployDirectory(), "pathplanner/" + name + ".path")))){
            StringBuilder fileContentBuilder = new StringBuilder();
            String line;
            while((line = br.readLine()) != null){
                fileContentBuilder.append(line);
            }

            String fileContent = fileContentBuilder.toString();
            JSONObject json = (JSONObject) new JSONParser().parse(fileContent);

            ArrayList<Waypoint> waypoints = getWaypointsFromJson(json);
            ArrayList<EventMarker> markers = getMarkersFromJson(json);

            return new PathPlannerTrajectory(waypoints, markers, constraints, reversed);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load a path file from storage
     * @param name The name of the path to load
     * @param constraints Max velocity and acceleration constraints of the path
     * @return The generated path
     */
    public static PathPlannerTrajectory loadPath(String name, PathConstraints constraints){
        return loadPath(name, constraints, false);
    }

    /**
     * Load a path from storage
     * @param name The name of the path to load
     * @param maxVel Max velocity of the path
     * @param maxAccel Max velocity of the path
     * @param reversed Should the robot follow the path reversed
     * @return The generated path
     */
    public static PathPlannerTrajectory loadPath(String name, double maxVel, double maxAccel, boolean reversed){
        return loadPath(name, new PathConstraints(maxVel, maxAccel), reversed);
    }

    /**
     * Load a path from storage
     * @param name The name of the path to load
     * @param maxVel Max velocity of the path
     * @param maxAccel Max velocity of the path
     * @return The generated path
     */
    public static PathPlannerTrajectory loadPath(String name, double maxVel, double maxAccel){
        return loadPath(name, new PathConstraints(maxVel, maxAccel), false);
    }

    /**
     * Load a path file from storage as a path group. This will separate the path into multiple
     * paths based on the waypoints marked as "stop points"
     * @param name The name of the path group to load
     * @param reversed Should the robot follow this path group reversed
     * @param constraint The PathConstraints (max velocity, max acceleration) of the first path in the group
     * @param constraints The PathConstraints (max velocity, max acceleration) of the remaining paths in the group. If there are less constraints than paths, the last constrain given will be used for the remaining paths.
     * @return An ArrayList of all generated paths in the group
     */
    public static ArrayList<PathPlannerTrajectory> loadPathGroup(String name, boolean reversed, PathConstraints constraint, PathConstraints... constraints){
        ArrayList<PathConstraints> allConstraints = new ArrayList<>();
        allConstraints.add(constraint);
        allConstraints.addAll(Arrays.asList(constraints));

        try(BufferedReader br = new BufferedReader(new FileReader(new File(Filesystem.getDeployDirectory(), "pathplanner/" + name + ".path")))){
            StringBuilder fileContentBuilder = new StringBuilder();
            String line;
            while((line = br.readLine()) != null){
                fileContentBuilder.append(line);
            }

            String fileContent = fileContentBuilder.toString();
            JSONObject json = (JSONObject) new JSONParser().parse(fileContent);

            ArrayList<Waypoint> waypoints = getWaypointsFromJson(json);
            ArrayList<EventMarker> markers = getMarkersFromJson(json);

            ArrayList<ArrayList<Waypoint>> splitWaypoints = new ArrayList<>();
            ArrayList<ArrayList<EventMarker>> splitMarkers = new ArrayList<>();

            ArrayList<Waypoint> currentPath = new ArrayList<>();
            for(int i = 0; i < waypoints.size(); i++){
                Waypoint w = waypoints.get(i);

                currentPath.add(w);
                if(w.isStopPoint || i == waypoints.size() - 1){
                    // Get the markers that should be part of this path and correct their positions
                    ArrayList<EventMarker> currentMarkers = new ArrayList<>();
                    for(EventMarker marker : markers){
                        if(marker.waypointRelativePos >= waypoints.indexOf(currentPath.get(0)) && marker.waypointRelativePos <= waypoints.indexOf(w)){
                            currentMarkers.add(new EventMarker(marker.name, marker.waypointRelativePos - waypoints.indexOf(currentPath.get(0))));
                        }
                    }
                    splitMarkers.add(currentMarkers);

                    splitWaypoints.add(currentPath);
                    currentPath = new ArrayList<>();
                    currentPath.add(w);
                }
            }

            if(splitWaypoints.size() != splitMarkers.size()){
                throw new RuntimeException("Size of splitWaypoints does not match splitMarkers. Something went very wrong");
            }

            ArrayList<PathPlannerTrajectory> pathGroup = new ArrayList<>();
            for(int i = 0; i < splitWaypoints.size(); i++){
                PathConstraints currentConstraints;
                if(i > allConstraints.size() - 1){
                    currentConstraints = allConstraints.get(allConstraints.size() - 1);
                }else{
                    currentConstraints = allConstraints.get(i);
                }

                pathGroup.add(new PathPlannerTrajectory(splitWaypoints.get(i), splitMarkers.get(i), currentConstraints, reversed));
            }

            return pathGroup;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load a path file from storage as a path group. This will separate the path into multiple
     * paths based on the waypoints marked as "stop points"
     * @param name The name of the path group to load
     * @param constraint The PathConstraints (max velocity, max acceleration) of the first path in the group
     * @param constraints The PathConstraints (max velocity, max acceleration) of the remaining paths in the group. If there are less constraints than paths, the last constrain given will be used for the remaining paths.
     * @return An ArrayList of all generated paths in the group
     */
    public static ArrayList<PathPlannerTrajectory> loadPathGroup(String name, PathConstraints constraint, PathConstraints... constraints){
        return loadPathGroup(name, false, constraint, constraints);
    }

    /**
     * Load a path file from storage as a path group. This will separate the path into multiple
     * paths based on the waypoints marked as "stop points"
     * @param name The name of the path group to load
     * @param maxVel The max velocity of every path in the group
     * @param maxAccel The max acceleraiton of every path in the group
     * @param reversed Should the robot follow this path group reversed
     * @return An ArrayList of all generated paths in the group
     */
    public static ArrayList<PathPlannerTrajectory> loadPathGroup(String name, double maxVel, double maxAccel, boolean reversed){
        return loadPathGroup(name, reversed, new PathConstraints(maxVel, maxAccel));
    }

    /**
     * Load a path file from storage as a path group. This will separate the path into multiple
     * paths based on the waypoints marked as "stop points"
     * @param name The name of the path group to load
     * @param maxVel The max velocity of every path in the group
     * @param maxAccel The max acceleraiton of every path in the group
     * @return An ArrayList of all generated paths in the group
     */
    public static ArrayList<PathPlannerTrajectory> loadPathGroup(String name, double maxVel, double maxAccel){
        return loadPathGroup(name, false, new PathConstraints(maxVel, maxAccel));
    }

    private static ArrayList<Waypoint> getWaypointsFromJson(JSONObject json){
        JSONArray jsonWaypoints = (JSONArray) json.get("waypoints");

        ArrayList<Waypoint> waypoints = new ArrayList<>();

        for (Object waypoint : jsonWaypoints) {
            JSONObject jsonWaypoint = (JSONObject) waypoint;

            JSONObject jsonAnchor = (JSONObject) jsonWaypoint.get("anchorPoint");
            Translation2d anchorPoint = new Translation2d(((Number) jsonAnchor.get("x")).doubleValue(), ((Number) jsonAnchor.get("y")).doubleValue());

            JSONObject jsonPrevControl = (JSONObject) jsonWaypoint.get("prevControl");
            Translation2d prevControl = null;
            if (jsonPrevControl != null) {
                prevControl = new Translation2d(((Number) jsonPrevControl.get("x")).doubleValue(), ((Number) jsonPrevControl.get("y")).doubleValue());
            }

            JSONObject jsonNextControl = (JSONObject) jsonWaypoint.get("nextControl");
            Translation2d nextControl = null;
            if (jsonNextControl != null) {
                nextControl = new Translation2d(((Number) jsonNextControl.get("x")).doubleValue(), ((Number) jsonNextControl.get("y")).doubleValue());
            }

            Rotation2d holonomicAngle = Rotation2d.fromDegrees(((Number) jsonWaypoint.get("holonomicAngle")).doubleValue());
            boolean isReversal = (boolean) jsonWaypoint.get("isReversal");
            Object isStopPointObj = jsonWaypoint.get("isStopPoint");
            boolean isStopPoint = false;
            if(isStopPointObj != null) isStopPoint = (boolean) isStopPointObj;
            double velOverride = -1;
            if (jsonWaypoint.get("velOverride") != null) {
                velOverride = ((Number) jsonWaypoint.get("velOverride")).doubleValue();
            }

            waypoints.add(new Waypoint(anchorPoint, prevControl, nextControl, velOverride, holonomicAngle, isReversal, isStopPoint));
        }

        return waypoints;
    }

    private static ArrayList<EventMarker> getMarkersFromJson(JSONObject json){
        JSONArray jsonMarkers = (JSONArray) json.get("markers");

        ArrayList<EventMarker> markers = new ArrayList<>();

        if(jsonMarkers != null){
            for(Object marker : jsonMarkers){
                JSONObject jsonMarker = (JSONObject) marker;

                markers.add(new EventMarker((String) jsonMarker.get("name"), ((Number) jsonMarker.get("position")).doubleValue()));
            }
        }

        return markers;
    }
}