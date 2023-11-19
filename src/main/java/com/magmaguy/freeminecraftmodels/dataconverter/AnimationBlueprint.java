package com.magmaguy.freeminecraftmodels.dataconverter;

import com.magmaguy.freeminecraftmodels.utils.Developer;
import com.magmaguy.freeminecraftmodels.utils.LoopType;
import lombok.Getter;

import java.util.*;

public class AnimationBlueprint {
    @Getter
    private final HashMap<BoneBlueprint, List<Keyframe>> boneKeyframes = new HashMap<>();
    @Getter
    private final HashMap<BoneBlueprint, AnimationFrame[]> animationFrames = new HashMap<>();
    @Getter
    private LoopType loopType;
    @Getter
    private String animationName;
    private SkeletonBlueprint skeletonBlueprint;
    @Getter
    private int duration;

    public AnimationBlueprint(Object data, String modelName, SkeletonBlueprint skeletonBlueprint) {
        Map<String, Object> animationData;
        try {
            animationData = (Map<String, Object>) data;
        } catch (Exception e) {
            Developer.warn("Failed to get animation data! Model format is not as expected, this version of BlockBench is not compatible with FreeMinecraftModels!");
            e.printStackTrace();
            return;
        }

        this.skeletonBlueprint = skeletonBlueprint;
        initializeGlobalValues(animationData);

        //In BBModel files, each bone holds the data for their transformations, so data is stored from the bone's perspective
        ((Map<String, Object>) animationData.get("animators")).entrySet().forEach(pair -> initializeBones((Map<String, Object>) pair.getValue()));

        //Process the keyframes
        interpolateKeyframes();
    }

    public static double lerp(double start, double end, double t) {
        return (1 - t) * start + t * end;
    }

    private void initializeGlobalValues(Map<String, Object> animationData) {
        //Parse global data for animation
        animationName = (String) animationData.get("name");
        loopType = LoopType.valueOf(((String) animationData.get("loop")).toUpperCase());
        duration = (int) (20 * (Double) animationData.get("length"));
    }

    private void initializeBones(Map<String, Object> animationData) {
        String boneName = (String) animationData.get("name");
        BoneBlueprint boneBlueprint = skeletonBlueprint.getBoneMap().get(boneName);
        if (boneBlueprint == null) {
            Developer.warn("Failed to get bone " + boneName + " from model!");
            return;
        }
        List<Keyframe> keyframes = new ArrayList<>();
        for (Object keyframeData : ((List) animationData.get("keyframes"))) {
            keyframes.add(new Keyframe(keyframeData));
        }
        keyframes.sort(Comparator.comparingInt(Keyframe::getTimeInTicks));
        boneKeyframes.put(boneBlueprint, keyframes);
    }

    private void interpolateKeyframes() {
        boneKeyframes.forEach(this::interpolateBoneKeyframes);
    }

    private void interpolateBoneKeyframes(BoneBlueprint boneBlueprint, List<Keyframe> keyframes) {
        List<Keyframe> rotationKeyframes = new ArrayList<>();
        List<Keyframe> positionKeyframes = new ArrayList<>();
        List<Keyframe> scaleKeyframes = new ArrayList<>();
        for (Keyframe keyframe : keyframes) {
            switch (keyframe.getTransformationType()) {
                case ROTATION -> rotationKeyframes.add(keyframe);
                case POSITION -> positionKeyframes.add(keyframe);
                case SCALE -> scaleKeyframes.add(keyframe);
            }
        }

        AnimationFrame[] animationFramesArray = new AnimationFrame[duration];
        for (int i = 0; i < animationFramesArray.length; i++)
            animationFramesArray[i] = new AnimationFrame();

        //Interpolation time
        interpolateRotations(animationFramesArray, rotationKeyframes);
        interpolateTranslations(animationFramesArray, positionKeyframes);
        //interpolateScale(animationFramesArray, scaleKeyframes);

        this.animationFrames.put(boneBlueprint, animationFramesArray);
    }

    private void interpolateRotations(AnimationFrame[] animationFramesArray, List<Keyframe> rotationKeyframes) {
        Keyframe firstFrame = null;
        Keyframe previousFrame = null;
        Keyframe lastFrame = null;
        for (int i = 0; i < rotationKeyframes.size(); i++) {
            Keyframe animationFrame = rotationKeyframes.get(i);
            if (i == 0) {
                firstFrame = animationFrame;
                previousFrame = animationFrame;
                lastFrame = animationFrame;
                continue;
            }
            int durationBetweenKeyframes = animationFrame.getTimeInTicks() - previousFrame.getTimeInTicks();
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                int currentFrame = j + previousFrame.getTimeInTicks();
                animationFramesArray[currentFrame].xRotation = lerp(previousFrame.getDataX(), animationFrame.getDataX(), j / (double) durationBetweenKeyframes);
                animationFramesArray[currentFrame].yRotation = lerp(previousFrame.getDataY(), animationFrame.getDataY(), j / (double) durationBetweenKeyframes);
                animationFramesArray[currentFrame].zRotation = lerp(previousFrame.getDataZ(), animationFrame.getDataZ(), j / (double) durationBetweenKeyframes);
            }
            previousFrame = animationFrame;
            if (animationFrame.getTimeInTicks() > lastFrame.getTimeInTicks()) lastFrame = animationFrame;
            if (animationFrame.getTimeInTicks() < firstFrame.getTimeInTicks()) firstFrame = animationFrame;
        }
        if (lastFrame != null && lastFrame.getTimeInTicks() < duration - 1) {
            int durationBetweenKeyframes = duration - 1 - lastFrame.getTimeInTicks();
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                int currentFrame = j + previousFrame.getTimeInTicks();
                animationFramesArray[currentFrame].xRotation = lastFrame.getDataX();
                animationFramesArray[currentFrame].yRotation = lastFrame.getDataY();
                animationFramesArray[currentFrame].zRotation = lastFrame.getDataZ();
            }
        }
        if (firstFrame != null && firstFrame.getTimeInTicks() > 0) {
            int durationBetweenKeyframes = firstFrame.getTimeInTicks();
            durationBetweenKeyframes = Math.min(durationBetweenKeyframes, duration - 1);
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                animationFramesArray[j].xRotation = firstFrame.getDataX();
                animationFramesArray[j].yRotation = firstFrame.getDataY();
                animationFramesArray[j].zRotation = firstFrame.getDataZ();
            }
        }
    }

    private void interpolateTranslations(AnimationFrame[] animationFramesArray, List<Keyframe> positionKeyframes) {
        Keyframe firstFrame = null;
        Keyframe previousFrame = null;
        Keyframe lastFrame = null;
        for (int i = 0; i < positionKeyframes.size(); i++) {
            Keyframe animationFrame = positionKeyframes.get(i);
            if (i == 0) {
                firstFrame = animationFrame;
                previousFrame = animationFrame;
                lastFrame = animationFrame;
                continue;
            }
            int durationBetweenKeyframes = animationFrame.getTimeInTicks() - previousFrame.getTimeInTicks();
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                int currentFrame = j + previousFrame.getTimeInTicks();
                animationFramesArray[currentFrame].xPosition = lerp(-previousFrame.getDataX(), -animationFrame.getDataX(), j / (double) durationBetweenKeyframes) / 16d;
                animationFramesArray[currentFrame].yPosition = lerp(previousFrame.getDataY(), animationFrame.getDataY(), j / (double) durationBetweenKeyframes) / 16d;
                animationFramesArray[currentFrame].zPosition = lerp(previousFrame.getDataZ(), animationFrame.getDataZ(), j / (double) durationBetweenKeyframes) / 16d;
            }
            previousFrame = animationFrame;
            if (animationFrame.getTimeInTicks() > lastFrame.getTimeInTicks()) lastFrame = animationFrame;
            if (animationFrame.getTimeInTicks() < firstFrame.getTimeInTicks()) firstFrame = animationFrame;
        }
        if (lastFrame != null && lastFrame.getTimeInTicks() < duration - 1) {
            int durationBetweenKeyframes = duration - 1 - lastFrame.getTimeInTicks();
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                int currentFrame = j + previousFrame.getTimeInTicks();
                animationFramesArray[currentFrame].xPosition = -lastFrame.getDataX() /16d;
                animationFramesArray[currentFrame].yPosition = lastFrame.getDataY()/16d;
                animationFramesArray[currentFrame].zPosition = lastFrame.getDataZ()/16d;
            }
        }
        if (firstFrame != null && firstFrame.getTimeInTicks() > 0) {
            int durationBetweenKeyframes = firstFrame.getTimeInTicks();
            durationBetweenKeyframes = Math.min(durationBetweenKeyframes, duration - 1);
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                animationFramesArray[j].xPosition = -firstFrame.getDataX()/16d;
                animationFramesArray[j].yPosition = firstFrame.getDataY()/16d;
                animationFramesArray[j].zPosition = firstFrame.getDataZ()/16d;
            }
        }
    }

    //todo: Scale currently does nothing because this might require a custom NMS solution (?) contact me if you have a solution
    private void interpolateScales(AnimationFrame[] animationFramesArray, List<Keyframe> scaleKeyframes) {
        Keyframe previousFrame = null;
        for (int i = 0; i < scaleKeyframes.size(); i++) {
            Keyframe animationFrame = scaleKeyframes.get(i);
            if (i == 0) {
                previousFrame = animationFrame;
                continue;
            }
            int durationBetweenKeyframes = animationFrame.getTimeInTicks() - previousFrame.getTimeInTicks();
            for (int j = 0; j < durationBetweenKeyframes; j++) {
                int currentFrame = j + previousFrame.getTimeInTicks();
                animationFramesArray[currentFrame].xScale = lerp(previousFrame.getDataX(), animationFrame.getDataX(), j / (double) durationBetweenKeyframes); //note: probably needs a multiplier here depending on implementation
                animationFramesArray[currentFrame].yScale = lerp(previousFrame.getDataY(), animationFrame.getDataY(), j / (double) durationBetweenKeyframes);
                animationFramesArray[currentFrame].zScale = lerp(previousFrame.getDataZ(), animationFrame.getDataZ(), j / (double) durationBetweenKeyframes);
            }
            previousFrame = animationFrame;
        }
    }
}
