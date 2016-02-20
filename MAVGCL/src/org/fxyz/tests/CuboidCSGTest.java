/*
 * Copyright (C) 2013-2015 F(X)yz, 
 * Sean Phillips, Jason Pollastrini and Jose Pereda
 * All rights reserved.
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
package org.fxyz.tests;

import java.util.function.Function;

import org.fxyz.cameras.CameraTransformer;
import org.fxyz.geometry.Point3D;
import org.fxyz.shapes.primitives.CSGMesh;
import org.fxyz.shapes.primitives.CuboidMesh;
import org.fxyz.shapes.primitives.PrismMesh;
import org.fxyz.shapes.primitives.helper.TriangleMeshHelper;
import org.fxyz.utils.MeshUtils;
import org.fxyz.utils.OBJWriter;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Transform;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

/**
 *
 * @author jpereda
 */
public class CuboidCSGTest extends Application {
    private PerspectiveCamera camera;
    private final double sceneWidth = 600;
    private final double sceneHeight = 600;
    private final CameraTransformer cameraTransform = new CameraTransformer();
    
    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;
    private double mouseDeltaX;
    private double mouseDeltaY;
    private CuboidMesh cuboid;
    private Rotate rotateY;
    
    private Function<Point3D, Number> dens = p-> p.x;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        Group sceneRoot = new Group();
        Scene scene = new Scene(sceneRoot, sceneWidth, sceneHeight, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.WHITESMOKE);
        camera = new PerspectiveCamera(true);        
     
        //setup camera transform for rotational support
        cameraTransform.setTranslate(0, 0, 0);
        cameraTransform.getChildren().add(camera);
        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        camera.setTranslateZ(-30);
        cameraTransform.ry.setAngle(-45.0);
        cameraTransform.rx.setAngle(-10.0);
        //add a Point Light for better viewing of the grid coordinate system
        PointLight light = new PointLight(Color.WHITE);
        cameraTransform.getChildren().add(light);
        light.setTranslateX(camera.getTranslateX());
        light.setTranslateY(camera.getTranslateY());
        light.setTranslateZ(10*camera.getTranslateZ());        
        scene.setCamera(camera);
        
        rotateY = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
        Group group = new Group();
        group.getChildren().add(cameraTransform);    
        cuboid = new CuboidMesh(10f,12f,4f,2,new Point3D(1f, -1f, 2f));
        cuboid.setDrawMode(DrawMode.LINE);
        
        CSG cuboidCSG = MeshUtils.mesh2CSG(cuboid.getMesh());
        CSG cube = new Cube(5).toCSG().color(Color.RED);

        PrismMesh prism = new PrismMesh(4f,8f);
        prism.setSectionType(TriangleMeshHelper.SectionType.TRIANGLE);
        CSG prismCSG = MeshUtils.mesh2CSG(prism.getMesh());
        CSG union = cuboidCSG.difference(cube.transformed(Transform.unity().translateX(5)))
                .union(prismCSG.transformed(Transform.unity().translateX(-4).rotZ(90)));
        CSGMesh unionMesh = new CSGMesh(union);
//        unionMesh.setDrawMode(DrawMode.LINE);
//        
//        unionMesh.setCullFace(CullFace.NONE);
    // NONE
//        cuboid.setTextureModeNone(Color.ROYALBLUE);
//        unionMesh.setTextureModeNone(Color.ROYALBLUE);
        // IMAGE
//        cuboid.setTextureModeImage(getClass().getResource("res/netCuboid.png").toExternalForm());
    // DENSITY
//        cuboid.setTextureModeVertices3D(1530,p->(double)p.x*p.y);
        unionMesh.setTextureModeVertices3D(1530,p->(double)p.x*p.y);
//        unionMesh.setTextureModeVertices1D(1530,p->(double)p);
    // FACES
//        cuboid.setTextureModeFaces(1530);
//        unionMesh.setTextureModeFaces(1530);

        
//        cuboid.getTransforms().addAll(new Rotate(0,Rotate.X_AXIS),rotateY);
//        group.getChildren().add(cuboid);
        unionMesh.getTransforms().addAll(new Rotate(0,Rotate.X_AXIS),rotateY);
        group.getChildren().add(unionMesh);
        
        sceneRoot.getChildren().addAll(group);        
        
        //First person shooter keyboard movement 
        scene.setOnKeyPressed(event -> {
            double change = 10.0;
            //Add shift modifier to simulate "Running Speed"
            if(event.isShiftDown()) { change = 50.0; }
            //What key did the user press?
            KeyCode keycode = event.getCode();
            //Step 2c: Add Zoom controls
            if(keycode == KeyCode.W) { camera.setTranslateZ(camera.getTranslateZ() + change); }
            if(keycode == KeyCode.S) { camera.setTranslateZ(camera.getTranslateZ() - change); }
            //Step 2d:  Add Strafe controls
            if(keycode == KeyCode.A) { camera.setTranslateX(camera.getTranslateX() - change); }
            if(keycode == KeyCode.D) { camera.setTranslateX(camera.getTranslateX() + change); }
        });        
        
        scene.setOnMousePressed((MouseEvent me) -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });
        scene.setOnMouseDragged((MouseEvent me) -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseDeltaX = (mousePosX - mouseOldX);
            mouseDeltaY = (mousePosY - mouseOldY);
            
            double modifier = 10.0;
            double modifierFactor = 0.1;
            
            if (me.isControlDown()) {
                modifier = 0.1;
            }
            if (me.isShiftDown()) {
                modifier = 50.0;
            }
            if (me.isPrimaryButtonDown()) {
                cameraTransform.ry.setAngle(((cameraTransform.ry.getAngle() + mouseDeltaX * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180);  // +
                cameraTransform.rx.setAngle(((cameraTransform.rx.getAngle() - mouseDeltaY * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180);  // -
            } else if (me.isSecondaryButtonDown()) {
                double z = camera.getTranslateZ();
                double newZ = z + mouseDeltaX * modifierFactor * modifier;
                camera.setTranslateZ(newZ);
            } else if (me.isMiddleButtonDown()) {
                cameraTransform.t.setX(cameraTransform.t.getX() + mouseDeltaX * modifierFactor * modifier * 0.3);  // -
                cameraTransform.t.setY(cameraTransform.t.getY() + mouseDeltaY * modifierFactor * modifier * 0.3);  // -
            }
        });
        
        primaryStage.setTitle("F(X)yz & JCSG - Boolean Operations Test");
        primaryStage.setScene(scene);
        primaryStage.show();        
        
        OBJWriter writer=new OBJWriter((TriangleMesh) unionMesh.getMesh(),"cuboid");
//        writer.setMaterialColor(Color.AQUA);
//        writer.setTextureImage(getClass().getResource("res/netCuboid.png").toExternalForm());
        writer.setTextureColors(1530);
        writer.exportMesh();
        
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }    
}
