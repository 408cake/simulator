package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Main extends ApplicationAdapter {

    public enum DroneState { ROAMING, LOCKON, ATTACKING, DESTROYED }

    public static class CombatDrone {
        public DroneState state = DroneState.ROAMING;
        public float x, y, width = 64f, height = 48f;
        public float destX, destY;
        public float minX, maxX, minY, maxY;
        public float speed = 160f;

        public float stateTimer = 0f;
        public float attackCooldown = 3.5f;
        public float lockonTime = 1.6f;
        public boolean isDownwardShot = false;

        public float swayTimer = 0f;

        public CombatDrone(float x, float y, float minX, float maxX, float minY, float maxY) {
            this.x = x;
            this.y = y;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            pickNewDestination();
            this.attackCooldown = MathUtils.random(3.0f, 5.0f);
            this.swayTimer = MathUtils.random(0f, 10f);
        }

        public void pickNewDestination() {
            this.destX = MathUtils.random(minX, maxX);
            this.destY = MathUtils.random(minY, maxY);
        }

        public void update(float dt, float playerX, float playerY, Main main) {
            if (state == DroneState.DESTROYED) return;

            stateTimer += dt;
            swayTimer += dt * 2.2f;

            switch (state) {
                case ROAMING:
                    float dx = destX - x;
                    float dy = destY - y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (dist > 15f) {
                        x += (dx / dist) * speed * dt;
                        y += (dy / dist) * speed * dt;
                    } else {
                        pickNewDestination();
                    }

                    y += MathUtils.sin(stateTimer * 4f) * 0.8f;

                    attackCooldown -= dt;
                    if (attackCooldown <= 0f) {
                        state = DroneState.LOCKON;
                        stateTimer = 0f;
                        isDownwardShot = (y - playerY) > 60f;
                    }
                    break;

                case LOCKON:
                    x += MathUtils.sin(stateTimer * 16f) * 1.2f;
                    if (stateTimer >= lockonTime) {
                        state = DroneState.ATTACKING;
                        stateTimer = 0f;
                    }
                    break;

                case ATTACKING:
                    if (stateTimer >= 0.35f) {
                        main.triggerPlayerDeath();
                        state = DroneState.ROAMING;
                        attackCooldown = 4.0f;
                    }
                    break;
            }
        }

        public float getScreenSwayX(float scale) {
            float wanderX = MathUtils.sin(swayTimer) * 90f + MathUtils.cos(swayTimer * 0.6f) * 45f;
            return wanderX * Math.min(1.5f, scale);
        }
    }

    private enum GameState { INTRO, GAMEPLAY }
    private GameState gameState = GameState.INTRO;

    private Array<Texture> introLogos;
    private int currentLogoIndex = 0;

    private enum IntroPhase { FADE_IN, HOLD, FADE_OUT }
    private IntroPhase introPhase = IntroPhase.FADE_IN;

    private float introTimer = 0f;
    private static final float FADE_IN_TIME = 1.2f;
    private static final float HOLD_TIME = 2.4f;
    private static final float FADE_OUT_TIME = 1.2f;
    private float logoAlpha = 0f;
    private Music introMusic;

    // 일시정지 제어
    private boolean isPaused = false;
    private final Rectangle pauseResumeBtn = new Rectangle(320, 240, 160, 42);
    private final Rectangle pauseRestartBtn = new Rectangle(320, 180, 160, 42);
    private final Rectangle pauseExitBtn = new Rectangle(320, 120, 160, 42);

    private String currentStageId = "01_testshoot";
    private String nextStageId = "";
    private String stageTitle = "";
    private String stageHint = "";
    private boolean stageClear = false;
    private boolean isPlayerDead = false;

    // 타이머 제어 (-1이면 시간제한 없음)
    private static final float STAGE_TIME_LIMIT = 75.0f;
    private float stageTimer = -1f;
    private boolean isTimeOver = false;

    private final IntArray targetsRequiredHits = new IntArray();
    private int maxAmmo = 5;
    private int bulletsLeft = maxAmmo;

    private final Array<CombatDrone> drones = new Array<>();
    private Texture droneIdleTexture;
    private Texture shot1Sheet, shot2Sheet;
    private Animation<TextureRegion> shot1Anim;
    private Animation<TextureRegion> shot2Anim;

    private static final float GROUPING_RADIUS_NORM = 0.14f;

    private final Rectangle singleCenterBtn = new Rectangle(335, 95, 130, 42);
    private final Rectangle modalRestartBtn = new Rectangle(260, 95, 130, 42);
    private final Rectangle modalNextBtn = new Rectangle(410, 95, 130, 42);

    private float worldWidth = 2600;
    private static final float MOVE_SPEED = 200;
    private static final float RUN_SPEED = 320;
    private static final float JUMP_SPEED = 500;
    private static final float BASE_GRAVITY = 2100;

    private static final float GUN_BODY_WIDTH = 1011f;
    private static final float GUN_BODY_HEIGHT = 758f;
    private static final float FRONT_SIGHT_W = 64f;
    private static final float FRONT_SIGHT_H = 96f;

    private static final float MAX_BREATH = 100f;
    private float currentBreath = MAX_BREATH;
    private boolean isHoldingBreath = false;

    private enum PlayerMotionState { IDLE, WALK, RUN }
    private PlayerMotionState motionState = PlayerMotionState.IDLE;

    private enum AimStance { NONE, STAND, CROUCH, PRONE }
    private AimStance currentStance = AimStance.NONE;
    private boolean wasAimingLastFrame = false;

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    private Texture bgTexture;
    private Texture stage3BgTexture;
    private Texture idleSheet, walkSheet, runSheet;
    private Animation<TextureRegion> idleAnim;
    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> runAnim;
    private float stateTime = 0f;
    private boolean facingLeft = false;

    private Texture blockTexture, targetTexture;
    private Texture gunBodyTexture, frontSightTexture;

    private Sound shotSound;
    private Sound droneSound;
    private long droneSoundId = -1;

    private OrthographicCamera camera;
    private FitViewport viewport;
    private OrthographicCamera uiCamera;

    private final Rectangle player = new Rectangle(100, 100, 36, 120);
    private final Array<Rectangle> blocks = new Array<>();
    private final Array<Rectangle> targets = new Array<>();
    private final Array<Array<Vector2>> bulletHoles = new Array<>();

    private float velocityX, velocityY;
    private boolean grounded;

    private boolean isInspectingTarget = false;
    private int inspectingTargetIndex = 0;

    private final Vector3 mousePos = new Vector3();

    private float aimBaseX = 400f;
    private float aimBaseY = 300f;
    private float swayTime = 0f;
    private float bodyOffsetY = -60f;

    private float gunBodyX = 400f;
    private float gunBodyY = 240f;
    private final Vector3 frontSightPos = new Vector3(400, 300, 0);

    private float shootCooldown = 0f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        uiCamera = new OrthographicCamera(800, 600);
        uiCamera.position.set(400, 300, 0);
        uiCamera.update();

        introLogos = new Array<>();
        if (Gdx.files.internal("logo_studio.png").exists()) {
            introLogos.add(new Texture(Gdx.files.internal("logo_studio.png")));
        }
        if (Gdx.files.internal("logo_engine.png").exists()) {
            introLogos.add(new Texture(Gdx.files.internal("logo_engine.png")));
        }

        if (Gdx.files.internal("intro.mp3").exists()) {
            introMusic = Gdx.audio.newMusic(Gdx.files.internal("intro.mp3"));
        } else if (Gdx.files.internal("intro.wav").exists()) {
            introMusic = Gdx.audio.newMusic(Gdx.files.internal("intro.wav"));
        }

        if (introLogos.size == 0) {
            gameState = GameState.GAMEPLAY;
        } else if (introMusic != null) {
            introMusic.setLooping(false);
            introMusic.play();
        }
        font = new BitmapFont(Gdx.files.internal("font.fnt"));
        font.getData().setScale(0.20f);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        bgTexture = new Texture(Gdx.files.internal("01_background.png"));

        if (Gdx.files.internal("03_background.png").exists()) {
            stage3BgTexture = new Texture(Gdx.files.internal("03_background.png"));
        } else {
            stage3BgTexture = bgTexture;
        }

        idleSheet = new Texture(Gdx.files.internal("Idle.png"));
        walkSheet = new Texture(Gdx.files.internal("Walk.png"));
        runSheet  = new Texture(Gdx.files.internal("Run.png"));

        TextureRegion[][] idleTmp = TextureRegion.split(idleSheet, idleSheet.getWidth() / 9, idleSheet.getHeight());
        idleAnim = new Animation<>(0.12f, idleTmp[0]);
        idleAnim.setPlayMode(Animation.PlayMode.LOOP);

        TextureRegion[][] walkTmp = TextureRegion.split(walkSheet, walkSheet.getWidth() / 8, walkSheet.getHeight());
        walkAnim = new Animation<>(0.10f, walkTmp[0]);
        walkAnim.setPlayMode(Animation.PlayMode.LOOP);

        TextureRegion[][] runTmp = TextureRegion.split(runSheet, runSheet.getWidth() / 8, runSheet.getHeight());
        runAnim = new Animation<>(0.08f, runTmp[0]);
        runAnim.setPlayMode(Animation.PlayMode.LOOP);

        blockTexture = new Texture(Gdx.files.internal("block.png"));
        targetTexture = new Texture(Gdx.files.internal("target.png"));
        gunBodyTexture = new Texture(Gdx.files.internal("gun_body.png"));
        frontSightTexture = new Texture(Gdx.files.internal("front_sight.png"));

        if (Gdx.files.internal("drone_idle.png").exists()) {
            droneIdleTexture = new Texture(Gdx.files.internal("drone_idle.png"));
        } else {
            droneIdleTexture = targetTexture;
        }

        if (Gdx.files.internal("shot 1.png").exists()) {
            shot1Sheet = new Texture(Gdx.files.internal("shot 1.png"));
            TextureRegion[][] s1 = TextureRegion.split(shot1Sheet, shot1Sheet.getWidth() / 5, shot1Sheet.getHeight());
            shot1Anim = new Animation<>(0.07f, s1[0]);
        }
        if (Gdx.files.internal("shot 2.png").exists()) {
            shot2Sheet = new Texture(Gdx.files.internal("shot 2.png"));
            TextureRegion[][] s2 = TextureRegion.split(shot2Sheet, shot2Sheet.getWidth() / 5, shot2Sheet.getHeight());
            shot2Anim = new Animation<>(0.07f, s2[0]);
        }

        if (Gdx.files.internal("drone.mp3").exists()) {
            droneSound = Gdx.audio.newSound(Gdx.files.internal("drone.mp3"));
        } else if (Gdx.files.internal("drone.wav").exists()) {
            droneSound = Gdx.audio.newSound(Gdx.files.internal("drone.wav"));
        }

        if (Gdx.files.internal("shot.mp3").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.mp3"));
        } else if (Gdx.files.internal("shot.wav").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.wav"));
        }

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 360);
        viewport = new FitViewport(480, 360, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        loadStage("01_testshoot");
    }

    private boolean isStage3() {
        return currentStageId.contains("03") || currentStageId.contains("drone") || drones.size > 0;
    }

    private void stopDroneSound() {
        if (droneSound != null && droneSoundId != -1) {
            droneSound.stop(droneSoundId);
            droneSoundId = -1;
        }
    }

    public void triggerPlayerDeath() {
        if (!stageClear && !isPlayerDead) {
            isPlayerDead = true;
            stopDroneSound();
            currentStance = AimStance.NONE;
            isInspectingTarget = true;
        }
    }

    public void loadStage(String stageId) {
        stopDroneSound();
        isPaused = false;

        String path = "stages/" + stageId + ".json";
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.error("StageLoader", "파일을 찾을 수 없습니다: " + path);
            return;
        }

        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        currentStageId = root.getString("id", stageId);
        stageTitle = root.getString("name", "스테이지");
        nextStageId = root.getString("nextStage", "");
        worldWidth = root.getFloat("width", 2600f);
        stageHint = root.getString("hint", "");

        // 1단계는 5발 제한, 그 외 스테이지는 JSON 파싱(기본 18발)
        if (currentStageId.contains("01")) {
            maxAmmo = 5;
            stageTimer = -1f; // 1단계는 시간제한 없음
        } else {
            maxAmmo = root.getInt("maxAmmo", 18);
            stageTimer = root.getFloat("timeLimit", STAGE_TIME_LIMIT);
        }

        bulletsLeft = maxAmmo;
        stageClear = false;
        isPlayerDead = false;
        isTimeOver = false;

        currentStance = AimStance.NONE;
        isInspectingTarget = false;
        inspectingTargetIndex = 0;

        float spawnX = root.getFloat("spawnX", 100f);
        float spawnY = root.getFloat("spawnY", 100f);
        player.setPosition(spawnX, spawnY);
        velocityX = 0;
        velocityY = 0;

        blocks.clear();
        JsonValue platforms = root.get("platforms");
        if (platforms != null) {
            for (JsonValue p = platforms.child; p != null; p = p.next) {
                blocks.add(new Rectangle(p.getFloat("x"), p.getFloat("y"), p.getFloat("width"), p.getFloat("height")));
            }
        }

        targets.clear();
        bulletHoles.clear();
        targetsRequiredHits.clear();

        JsonValue tgList = root.get("targets");
        if (tgList != null) {
            for (JsonValue t = tgList.child; t != null; t = t.next) {
                targets.add(new Rectangle(t.getFloat("x"), t.getFloat("y"), t.getFloat("width", 36f), t.getFloat("height", 110f)));
                bulletHoles.add(new Array<>());
                targetsRequiredHits.add(t.getInt("requiredHits", t.getInt("targetHitsRequired", 5)));
            }
        }

        drones.clear();
        JsonValue drList = root.get("drones");
        if (drList != null && drList.child != null) {
            JsonValue d = drList.child;
            drones.add(new CombatDrone(
                d.getFloat("x"), d.getFloat("y"),
                d.getFloat("minX", d.getFloat("x") - 250f),
                d.getFloat("maxX", d.getFloat("x") + 250f),
                d.getFloat("minY", 160f),
                d.getFloat("maxY", 310f)
            ));
        } else if (stageId.contains("03") || stageId.contains("drone")) {
            drones.add(new CombatDrone(1100, 240, 700, 1500, 160, 310));
        }

        if (drones.size > 0 && droneSound != null) {
            droneSoundId = droneSound.loop(0.45f);
        }
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);

        if (gameState == GameState.INTRO) {
            updateIntro(dt);
            renderIntro();
            return;
        }

        if (!isPaused) {
            update(dt);
        } else {
            handlePauseInput();
        }

        ScreenUtils.clear(0.85f, 0.9f, 0.95f, 1f);
        viewport.apply();

        if (currentStance != AimStance.NONE) {
            renderFirstPersonView();
            renderBreathUI();
        } else {
            renderSideView();
        }

        renderHUD();

        if (isInspectingTarget) {
            renderTargetInspectionModal();
        }

        if (isPaused) {
            renderPauseMenu();
        }
    }

    private void handlePauseInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            isPaused = false;
            return;
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());

            if (pauseResumeBtn.contains(mousePos.x, mousePos.y)) {
                isPaused = false;
            } else if (pauseRestartBtn.contains(mousePos.x, mousePos.y)) {
                isPaused = false;
                loadStage(currentStageId);
            } else if (pauseExitBtn.contains(mousePos.x, mousePos.y)) {
                Gdx.app.exit();
            }
        }
    }

    private void renderPauseMenu() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.82f);
        shapeRenderer.rect(0, 0, 800, 600);

        shapeRenderer.setColor(0.12f, 0.16f, 0.22f, 0.95f);
        shapeRenderer.rect(90, 310, 620, 200);

        shapeRenderer.setColor(0.2f, 0.5f, 0.85f, 1f);
        shapeRenderer.rect(pauseResumeBtn.x, pauseResumeBtn.y, pauseResumeBtn.width, pauseResumeBtn.height);

        shapeRenderer.setColor(0.85f, 0.45f, 0.15f, 1f);
        shapeRenderer.rect(pauseRestartBtn.x, pauseRestartBtn.y, pauseRestartBtn.width, pauseRestartBtn.height);

        shapeRenderer.setColor(0.8f, 0.2f, 0.2f, 1f);
        shapeRenderer.rect(pauseExitBtn.x, pauseExitBtn.y, pauseExitBtn.width, pauseExitBtn.height);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shapeRenderer.setColor(0.35f, 0.65f, 0.95f, 0.9f);
        shapeRenderer.rect(90, 310, 620, 200);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "=== 일시정지 ===", 345, 545);

        font.setColor(new Color(0.95f, 0.85f, 0.2f, 1f));
        font.draw(batch, "서서쏴 - 마우스 우클릭 , 앉아쏴 - C , 엎드려쏴 - Z", 170, 480);

        font.setColor(new Color(0.85f, 0.95f, 1.0f, 1f));
        font.draw(batch, "각 자세별 반동제어와 호흡회복속도가 다릅니다. 잘 이용하세요!", 135, 440);

        font.setColor(new Color(0.4f, 0.95f, 0.5f, 1f));
        font.draw(batch, "처음 맞힌 곳 기준으로 탄착군을 형성하세요!", 210, 400);

        font.setColor(new Color(0.85f, 0.95f, 1.0f, 1f));
        font.draw(batch, "E키를 누른 후, 화살표를 이용하여 표적지를 확인할 수 있습니다.", 125, 360);

        font.setColor(Color.WHITE);
        font.draw(batch, "계속하기", pauseResumeBtn.x + 46, pauseResumeBtn.y + 27);
        font.draw(batch, "다시시작", pauseRestartBtn.x + 46, pauseRestartBtn.y + 27);
        font.draw(batch, "게임 종료", pauseExitBtn.x + 46, pauseExitBtn.y + 27);
        batch.end();
    }

    private void updateIntro(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            finishIntro();
            return;
        }

        introTimer += dt;

        switch (introPhase) {
            case FADE_IN:
                logoAlpha = Math.min(1f, introTimer / FADE_IN_TIME);
                if (introTimer >= FADE_IN_TIME) {
                    introPhase = IntroPhase.HOLD;
                    introTimer = 0f;
                    logoAlpha = 1f;
                }
                break;

            case HOLD:
                logoAlpha = 1f;
                if (introTimer >= HOLD_TIME) {
                    introPhase = IntroPhase.FADE_OUT;
                    introTimer = 0f;
                }
                break;

            case FADE_OUT:
                logoAlpha = Math.max(0f, 1f - (introTimer / FADE_OUT_TIME));
                if (introTimer >= FADE_OUT_TIME) {
                    nextLogoOrStartGame();
                }
                break;
        }
    }

    private void finishIntro() {
        if (introMusic != null && introMusic.isPlaying()) {
            introMusic.stop();
        }
        gameState = GameState.GAMEPLAY;
    }

    private void nextLogoOrStartGame() {
        currentLogoIndex++;
        if (currentLogoIndex >= introLogos.size) {
            finishIntro();
        } else {
            introPhase = IntroPhase.FADE_IN;
            introTimer = 0f;
            logoAlpha = 0f;
        }
    }

    private void renderIntro() {
        ScreenUtils.clear(0.08f, 0.08f, 0.08f, 1f);
        if (currentLogoIndex >= introLogos.size) return;

        Texture currentLogo = introLogos.get(currentLogoIndex);

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        batch.setColor(1f, 1f, 1f, logoAlpha);

        float maxLogoW = 450f;
        float maxLogoH = 350f;
        float scale = Math.min(maxLogoW / currentLogo.getWidth(), maxLogoH / currentLogo.getHeight());

        float logoW = currentLogo.getWidth() * scale;
        float logoH = currentLogo.getHeight() * scale;
        float drawX = 400f - (logoW / 2f);
        float drawY = 300f - (logoH / 2f);

        batch.draw(currentLogo, drawX, drawY, logoW, logoH);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private TextureRegion getDroneFrame(CombatDrone drone) {
        if (drone.state == DroneState.ATTACKING) {
            Animation<TextureRegion> anim = drone.isDownwardShot ? shot2Anim : shot1Anim;
            if (anim != null) {
                return anim.getKeyFrame(drone.stateTimer, false);
            }
        }
        return new TextureRegion(droneIdleTexture);
    }

    private void renderSideView() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        Texture curBg = (isStage3() && stage3BgTexture != null) ? stage3BgTexture : bgTexture;

        float bgHeight = 360f;
        float bgScale = bgHeight / curBg.getHeight();
        float bgWidth = curBg.getWidth() * bgScale;
        for (float x = 0; x < worldWidth; x += bgWidth) {
            batch.draw(curBg, x, 0, bgWidth, bgHeight);
        }

        for (Rectangle block : blocks) {
            batch.draw(blockTexture, block.x, block.y, block.width, block.height);
        }

        for (Rectangle target : targets) {
            batch.draw(targetTexture, target.x, target.y, target.width, target.height);
        }

        for (CombatDrone drone : drones) {
            if (drone.state != DroneState.DESTROYED) {
                TextureRegion frame = getDroneFrame(drone);
                batch.draw(frame, drone.x, drone.y, drone.width, drone.height);
            }
        }

        TextureRegion currentFrame;
        switch (motionState) {
            case RUN:  currentFrame = runAnim.getKeyFrame(stateTime); break;
            case WALK: currentFrame = walkAnim.getKeyFrame(stateTime); break;
            default:   currentFrame = idleAnim.getKeyFrame(stateTime); break;
        }

        if (facingLeft && !currentFrame.isFlipX()) {
            currentFrame.flip(true, false);
        } else if (!facingLeft && currentFrame.isFlipX()) {
            currentFrame.flip(true, false);
        }

        float visualW = 90f;
        float visualH = 120f;
        float drawX = player.x + (player.width - visualW) / 2f;
        float drawY = player.y;

        batch.draw(currentFrame, drawX, drawY, visualW, visualH);

        int nearIdx = getNearbyTargetIndex();
        if (nearIdx != -1 && !isInspectingTarget) {
            font.setColor(Color.BLACK);
            font.draw(batch, "[E 키] 표적지 " + (nearIdx + 1) + " 확인", targets.get(nearIdx).x - 20, targets.get(nearIdx).y + targets.get(nearIdx).height + 25);
        }
        batch.end();

        if (drones.size > 0) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Gdx.gl.glLineWidth(2f);
            for (CombatDrone drone : drones) {
                if (drone.state == DroneState.LOCKON) {
                    shapeRenderer.setColor(1f, 0.1f, 0.1f, 0.85f);
                    shapeRenderer.line(drone.x + drone.width / 2f, drone.y + drone.height / 2f,
                        player.x + player.width / 2f, player.y + player.height * 0.7f);
                }
            }
            shapeRenderer.end();
            Gdx.gl.glLineWidth(1f);
        }
    }

    private void renderFirstPersonView() {
        float eyeY;
        if (currentStance == AimStance.PRONE) {
            eyeY = player.y + player.height * 0.25f;
        } else if (currentStance == AimStance.CROUCH) {
            eyeY = player.y + player.height * 0.55f;
        } else {
            eyeY = player.y + player.height * 0.85f;
        }

        if (isStage3() && stage3BgTexture != null) {
            batch.setProjectionMatrix(uiCamera.combined);
            batch.begin();
            float bgW = 800f;
            float bgH = 600f;
            float bgOffsetY = (300f - eyeY) * 0.5f;
            batch.draw(stage3BgTexture, 0, bgOffsetY, bgW, bgH);
            batch.end();
        } else {
            Array<Rectangle> forwardBlocks = new Array<>();
            for (Rectangle b : blocks) {
                if (b.x + b.width > player.x) forwardBlocks.add(b);
            }
            forwardBlocks.sort((a, b) -> Float.compare(b.x, a.x));

            shapeRenderer.setProjectionMatrix(uiCamera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            for (Rectangle b : forwardBlocks) {
                float dist = Math.max(1f, b.x - player.x);
                float depthAlpha = MathUtils.clamp(1f - (dist / (worldWidth - player.x)), 0.25f, 0.95f);
                shapeRenderer.setColor(0.35f * depthAlpha, 0.38f * depthAlpha, 0.42f * depthAlpha, 1f);
                float screenY = 300f + (b.y - eyeY);
                shapeRenderer.rect(0, screenY, 800, b.height);
            }
            shapeRenderer.end();
        }

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        Array<Integer> forwardTargetIndices = new Array<>();
        for (int i = 0; i < targets.size; i++) {
            if (targets.get(i).x + targets.get(i).width > player.x) {
                forwardTargetIndices.add(i);
            }
        }
        forwardTargetIndices.sort((i1, i2) -> Float.compare(targets.get(i2).x, targets.get(i1).x));

        for (int idx : forwardTargetIndices) {
            Rectangle target = targets.get(idx);
            float dist = Math.max(50f, target.x - player.x);
            float scale = MathUtils.clamp(400f / dist, 0.015f, 2.5f);
            float scaledW = Math.max(2f, 56f * scale);
            float scaledH = Math.max(4f, target.height * scale);
            float targetScreenY = 300f + (target.y - eyeY);
            float targetScreenX = 400f - (scaledW / 2f);

            batch.draw(targetTexture, targetScreenX, targetScreenY, scaledW, scaledH);
        }

        for (CombatDrone drone : drones) {
            if (drone.state != DroneState.DESTROYED && drone.x + drone.width > player.x) {
                float dist = Math.max(60f, drone.x - player.x);
                float scale = MathUtils.clamp(450f / dist, 0.4f, 2.6f);
                float dw = drone.width * scale;
                float dh = drone.height * scale;

                float screenX = 400f - (dw / 2f) + drone.getScreenSwayX(scale);
                float screenY = 300f + (drone.y - eyeY);

                TextureRegion frame = getDroneFrame(drone);
                batch.draw(frame, screenX, screenY, dw, dh);

                if (drone.state == DroneState.LOCKON) {
                    font.setColor(1f, 0.2f, 0.2f, 1f);
                    font.draw(batch, "★ LOCK-ON! 사격 요망 ★", screenX - 25, screenY + dh + 22);
                }
            }
        }
        batch.end();

        if (forwardTargetIndices.size > 0) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (int idx : forwardTargetIndices) {
                Rectangle target = targets.get(idx);
                float dist = Math.max(50f, target.x - player.x);
                float scale = MathUtils.clamp(400f / dist, 0.015f, 2.5f);
                float scaledW = Math.max(2f, 56f * scale);
                float scaledH = Math.max(4f, target.height * scale);
                float targetScreenY = 300f + (target.y - eyeY);
                float targetScreenX = 400f - (scaledW / 2f);

                if (idx < bulletHoles.size) {
                    for (Vector2 hole : bulletHoles.get(idx)) {
                        float hx = targetScreenX + hole.x * scaledW;
                        float hy = targetScreenY + hole.y * scaledH;
                        float holeR = Math.max(1.0f, 3.5f * scale);

                        shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1f);
                        shapeRenderer.circle(hx, hy, holeR + 0.8f);

                        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 1f);
                        shapeRenderer.circle(hx, hy, holeR);
                    }
                }
            }
            shapeRenderer.end();
        }

        batch.begin();
        float sightDrawX = frontSightPos.x - (FRONT_SIGHT_W / 2f) + 138f;
        float sightDrawY = frontSightPos.y - (FRONT_SIGHT_H / 2f) + 140f;
        batch.draw(frontSightTexture, sightDrawX, sightDrawY, FRONT_SIGHT_W, FRONT_SIGHT_H);

        batch.draw(
            gunBodyTexture,
            gunBodyX - (GUN_BODY_WIDTH / 2f),
            gunBodyY - (GUN_BODY_HEIGHT / 2f),
            GUN_BODY_WIDTH,
            GUN_BODY_HEIGHT
        );
        batch.end();
    }

    private void renderHUD() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(0.15f, 0.15f, 0.15f, 1f);

        if (drones.size > 0) {
            int aliveDrones = 0;
            for (CombatDrone d : drones) if (d.state != DroneState.DESTROYED) aliveDrones++;
            font.draw(batch, "[" + stageTitle + "] 잔여탄: " + bulletsLeft + " / " + maxAmmo +
                "  |  적 드론: " + aliveDrones + "기  (ESC: 일시정지, E: 표적확인)", 25, 575);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(stageTitle).append("] 잔여탄: ").append(bulletsLeft).append(" / ").append(maxAmmo).append("  |  ");
            for (int i = 0; i < targets.size; i++) {
                int count = i < bulletHoles.size ? bulletHoles.get(i).size : 0;
                int req = i < targetsRequiredHits.size ? targetsRequiredHits.get(i) : 5;
                sb.append("T").append(i + 1).append(": ").append(count).append("/").append(req).append("  ");
            }
            sb.append("(ESC: 일시정지, E: 표적확인)");
            font.draw(batch, sb.toString(), 25, 575);
        }

        // 시간제한이 있는 스테이지만 남은 시간 텍스트 출력
        if (stageTimer > 0f) {
            String timeStr = String.format("%.1f", Math.max(0f, stageTimer));
            Color timeColor = (stageTimer <= 5.0f) ? new Color(0.85f, 0.1f, 0.1f, 1f) : new Color(0.15f, 0.15f, 0.15f, 1f);
            font.setColor(timeColor);
            font.draw(batch, "남은 시간: " + timeStr + "초", 650, 575);
        }

        if (isPlayerDead) {
            font.setColor(new Color(1f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 적 드론의 공격에 피격당했습니다! 사망 ★", 25, 550);
        } else if (stageClear) {
            font.setColor(new Color(0.1f, 0.65f, 0.2f, 1f));
            font.draw(batch, "★ 임무 완수! 과녁 결과를 확인하세요. ★", 25, 550);
        } else if (isTimeOver) {
            font.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 시간 초과! 결과를 확인하고 다시 시도하세요. ★", 25, 550);
        } else if (bulletsLeft <= 0) {
            font.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 잔여 탄약 소진! 과녁 결과를 확인하세요. ★", 25, 550);
        } else if (!stageHint.isEmpty()) {
            font.setColor(0.15f, 0.15f, 0.15f, 1f);
            font.draw(batch, stageHint, 25, 555);
        }
        batch.end();
    }

    private void renderBreathUI() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.75f);
        shapeRenderer.rect(30, 30, 204, 18);

        if (isHoldingBreath) {
            shapeRenderer.setColor(0.2f, 0.8f, 1.0f, 0.95f);
        } else if (currentBreath < 30f) {
            shapeRenderer.setColor(0.9f, 0.2f, 0.2f, 0.95f);
        } else {
            shapeRenderer.setColor(0.4f, 0.85f, 0.4f, 0.95f);
        }

        float breathBarWidth = (currentBreath / MAX_BREATH) * 200f;
        shapeRenderer.rect(32, 32, breathBarWidth, 14);
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        String breathText = isHoldingBreath ? "호흡 [멈춤 - 격발 안정]" : "호흡 [SPACE 키 길게]";
        font.draw(batch, breathText, 32, 65);
        batch.end();
    }

    private boolean isTargetGroupingSuccess(int targetIdx) {
        if (targetIdx < 0 || targetIdx >= bulletHoles.size) return false;
        Array<Vector2> holes = bulletHoles.get(targetIdx);
        int req = (targetIdx < targetsRequiredHits.size) ? targetsRequiredHits.get(targetIdx) : 5;

        if (holes.size < req) return false;

        Vector2 base = holes.get(0);
        float modalTargetW = 250f;
        float modalTargetH = 360f;
        float maxAllowedDistPixel = GROUPING_RADIUS_NORM * modalTargetW;

        int inCount = 1;
        for (int i = 1; i < holes.size; i++) {
            Vector2 h = holes.get(i);
            float dx = (h.x - base.x) * modalTargetW;
            float dy = (h.y - base.y) * modalTargetH;
            if (Math.sqrt(dx * dx + dy * dy) <= maxAllowedDistPixel) {
                inCount++;
            }
        }
        return inCount >= req;
    }

    private boolean checkAllTargetsGroupingCleared() {
        if (drones.size > 0) {
            for (CombatDrone d : drones) {
                if (d.state != DroneState.DESTROYED) return false;
            }
            return true;
        }

        if (targets.size == 0) return false;
        for (int i = 0; i < targets.size; i++) {
            if (!isTargetGroupingSuccess(i)) return false;
        }
        return true;
    }

    private void renderTargetInspectionModal() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 1. 전체 모달 배경을 깔끔한 흰색(연한 아이보리 톤)으로 변경
        shapeRenderer.setColor(0.96f, 0.96f, 0.98f, 0.95f);
        shapeRenderer.rect(0, 0, 800, 600);

        if (drones.size > 0) {
            if (isPlayerDead) {
                shapeRenderer.setColor(0.85f, 0.2f, 0.2f, 1f);
                shapeRenderer.rect(singleCenterBtn.x, singleCenterBtn.y, singleCenterBtn.width, singleCenterBtn.height);
            } else if (stageClear) {
                shapeRenderer.setColor(0.18f, 0.65f, 0.28f, 1f);
                shapeRenderer.rect(singleCenterBtn.x, singleCenterBtn.y, singleCenterBtn.width, singleCenterBtn.height);
            }
            shapeRenderer.end();

            batch.setProjectionMatrix(uiCamera.combined);
            batch.begin();
            if (isPlayerDead) {
                font.setColor(0.85f, 0.15f, 0.15f, 1f);
                font.draw(batch, "★ 적 드론 피격으로 인한 전사 (KIA) ★", 280, 360);
                font.setColor(0.15f, 0.15f, 0.15f, 1f);
                font.draw(batch, "적 드론의 공격 전에 먼저 조준하여 격추해야 합니다.", 225, 300);
                font.setColor(Color.WHITE);
                font.draw(batch, "다시 시도", singleCenterBtn.x + 36, singleCenterBtn.y + 26);
            } else if (stageClear) {
                font.setColor(0.1f, 0.55f, 0.2f, 1f);
                font.draw(batch, "★ 적 드론 격추 완료! MISSION CLEAR ★", 260, 360);
                font.setColor(0.15f, 0.15f, 0.15f, 1f);
                font.draw(batch, "모든 위협을 성공적으로 무력화했습니다.", 265, 300);
                font.setColor(Color.WHITE);
                String nextText = nextStageId.isEmpty() ? "게임 종료" : "다음 단계";
                font.draw(batch, nextText, singleCenterBtn.x + 32, singleCenterBtn.y + 26);
            }
            batch.end();
            return;
        }

        float modalTargetX = 275f;
        float modalTargetY = 160f;
        float modalTargetW = 250f;
        float modalTargetH = 360f;

        boolean isSessionOver = (bulletsLeft <= 0 || stageClear || isTimeOver);

        if (isSessionOver) {
            shapeRenderer.setColor(0.85f, 0.45f, 0.15f, 1f);
            shapeRenderer.rect(modalRestartBtn.x, modalRestartBtn.y, modalRestartBtn.width, modalRestartBtn.height);

            if (stageClear && !isTimeOver) {
                shapeRenderer.setColor(0.18f, 0.65f, 0.28f, 1f);
                shapeRenderer.rect(modalNextBtn.x, modalNextBtn.y, modalNextBtn.width, modalNextBtn.height);
            }
        }
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        batch.draw(targetTexture, modalTargetX, modalTargetY, modalTargetW, modalTargetH);

        // 흰색 배경에 맞춰 텍스트를 검정색 및 시인성 높은 색상으로 출력
        font.setColor(0.15f, 0.15f, 0.15f, 1f);
        font.draw(batch, "표적 #" + (inspectingTargetIndex + 1) + " 탄착군 결과 (←, → 키로 다른 과녁 전환)", 230, 565);

        boolean curSuccess = isTargetGroupingSuccess(inspectingTargetIndex);
        int reqHits = (inspectingTargetIndex < targetsRequiredHits.size) ? targetsRequiredHits.get(inspectingTargetIndex) : 5;
        int curHoles = (inspectingTargetIndex < bulletHoles.size) ? bulletHoles.get(inspectingTargetIndex).size : 0;

        if (curSuccess) {
            font.setColor(0.1f, 0.6f, 0.2f, 1f);
            font.draw(batch, "[표적 #" + (inspectingTargetIndex + 1) + " 탄착군 통과 (" + curHoles + "/" + reqHits + ")]", 310, 150);
        } else {
            font.setColor(0.85f, 0.2f, 0.2f, 1f);
            font.draw(batch, "[표적 #" + (inspectingTargetIndex + 1) + " 미달성 (" + curHoles + "/" + reqHits + " 요구)]", 310, 150);
        }

        if (isSessionOver) {
            if (stageClear && !isTimeOver) {
                font.setColor(0.1f, 0.6f, 0.2f, 1f);
                font.draw(batch, "★ 전체 표적 임무 완료! 합격 ★", 315, 60);
            } else if (isTimeOver) {
                font.setColor(0.85f, 0.15f, 0.15f, 1f);
                font.draw(batch, "제한 시간 초과: 다시 시도하세요.", 280, 60);
            } else {
                font.setColor(0.85f, 0.15f, 0.15f, 1f);
                font.draw(batch, "전체 기준 미달: 재사격이 필요합니다.", 275, 60);
            }

            // 버튼 안의 글자는 흰색 유지
            font.setColor(Color.WHITE);
            font.draw(batch, "다시 시도", modalRestartBtn.x + 36, modalRestartBtn.y + 26);
            if (stageClear && !isTimeOver) {
                String nextText = nextStageId.isEmpty() ? "완료" : "다음 단계";
                font.draw(batch, nextText, modalNextBtn.x + 36, modalNextBtn.y + 26);
            }
        } else {
            font.setColor(0.35f, 0.35f, 0.35f, 1f);
            font.draw(batch, "[E 또는 ESC를 눌러 닫기]", 310, 80);
        }
        batch.end();

        if (inspectingTargetIndex >= 0 && inspectingTargetIndex < bulletHoles.size) {
            Array<Vector2> holes = bulletHoles.get(inspectingTargetIndex);

            if (holes.size > 0) {
                Vector2 first = holes.get(0);
                float centerX = modalTargetX + first.x * modalTargetW;
                float centerY = modalTargetY + first.y * modalTargetH;
                float radius = GROUPING_RADIUS_NORM * modalTargetW;

                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                Gdx.gl.glLineWidth(2f);
                shapeRenderer.setColor(curSuccess ? new Color(0.1f, 0.7f, 0.2f, 0.9f) : new Color(0.9f, 0.5f, 0.1f, 0.9f));
                shapeRenderer.circle(centerX, centerY, radius);
                shapeRenderer.end();
                Gdx.gl.glLineWidth(1f);
            }

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < holes.size; i++) {
                Vector2 hole = holes.get(i);
                float hx = modalTargetX + hole.x * modalTargetW;
                float hy = modalTargetY + hole.y * modalTargetH;

                if (i == 0) {
                    shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f);
                    shapeRenderer.circle(hx, hy, 9f);
                }

                shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1f);
                shapeRenderer.circle(hx, hy, 7.5f);

                shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
                shapeRenderer.circle(hx, hy, 5.5f);
            }
            shapeRenderer.end();
        }
    }

    private void warpMouseToGunRight() {
        float targetUiX = gunBodyX + (GUN_BODY_WIDTH / 2f) - 140f;
        float targetUiY = gunBodyY + 60f;

        Vector3 screenCoords = new Vector3(targetUiX, targetUiY, 0);
        uiCamera.project(screenCoords, viewport.getScreenX(), viewport.getScreenY(),
            viewport.getScreenWidth(), viewport.getScreenHeight());

        int mouseScreenX = (int) screenCoords.x;
        int mouseScreenY = (int) (Gdx.graphics.getHeight() - screenCoords.y);

        Gdx.input.setCursorPosition(mouseScreenX, mouseScreenY);
    }

    private void update(float dt) {
        if (dt <= 0) return;

        if (!isInspectingTarget && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            isPaused = true;
            currentStance = AimStance.NONE;
            return;
        }

        for (CombatDrone drone : drones) {
            drone.update(dt, player.x, player.y, this);
        }

        // 제한시간이 설정된 경우(stageTimer > 0)에만 타이머 감소
        if (!stageClear && !isInspectingTarget && !isTimeOver && !isPlayerDead) {
            if (stageTimer > 0f) {
                stageTimer -= dt;
                if (stageTimer <= 0f) {
                    stageTimer = 0f;
                    isTimeOver = true;
                    stopDroneSound();
                    currentStance = AimStance.NONE;
                    isInspectingTarget = true;
                    inspectingTargetIndex = 0;
                }
            }
        }

        stateTime += dt;
        if (shootCooldown > 0) shootCooldown -= dt;

        if (isInspectingTarget) {
            stopDroneSound();

            if (drones.size > 0) {
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                    uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());

                    if (isPlayerDead && singleCenterBtn.contains(mousePos.x, mousePos.y)) {
                        loadStage(currentStageId);
                        return;
                    } else if (stageClear && singleCenterBtn.contains(mousePos.x, mousePos.y)) {
                        if (!nextStageId.isEmpty()) {
                            loadStage(nextStageId);
                        } else {
                            Gdx.app.exit();
                        }
                        return;
                    }
                }
                return;
            }

            if (targets.size > 0) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
                    inspectingTargetIndex = (inspectingTargetIndex + 1) % targets.size;
                } else if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
                    inspectingTargetIndex = (inspectingTargetIndex - 1 + targets.size) % targets.size;
                }
            }

            boolean isSessionOver = (bulletsLeft <= 0 || stageClear || isTimeOver);
            if (isSessionOver) {
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                    uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());

                    if (modalRestartBtn.contains(mousePos.x, mousePos.y)) {
                        loadStage(currentStageId);
                        return;
                    }
                    if (stageClear && !isTimeOver && modalNextBtn.contains(mousePos.x, mousePos.y)) {
                        if (!nextStageId.isEmpty()) loadStage(nextStageId);
                        return;
                    }
                }
            } else {
                if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                    isInspectingTarget = false;
                }
            }
            return;
        }

        if (targets.size > 0 && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            int nearIdx = getNearbyTargetIndex();
            isInspectingTarget = true;
            inspectingTargetIndex = (nearIdx != -1) ? nearIdx : 0;
            currentStance = AimStance.NONE;
            stageClear = checkAllTargetsGroupingCleared();
            return;
        }

        if (isTimeOver || isPlayerDead) {
            currentStance = AimStance.NONE;
        } else {
            if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
                currentStance = (currentStance == AimStance.PRONE) ? AimStance.NONE : AimStance.PRONE;
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
                currentStance = (currentStance == AimStance.CROUCH) ? AimStance.NONE : AimStance.CROUCH;
            }

            if (currentStance != AimStance.PRONE && currentStance != AimStance.CROUCH) {
                if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                    currentStance = AimStance.STAND;
                } else {
                    currentStance = AimStance.NONE;
                }
            }
        }

        boolean isAimingNow = (currentStance != AimStance.NONE);
        if (isAimingNow && !wasAimingLastFrame) {
            warpMouseToGunRight();
        }
        wasAimingLastFrame = isAimingNow;

        if (currentStance != AimStance.NONE) {
            velocityX = 0;
            motionState = PlayerMotionState.IDLE;

            if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && currentBreath > 0) {
                isHoldingBreath = true;
                currentBreath = Math.max(0f, currentBreath - 35f * dt);
            } else {
                isHoldingBreath = false;
                float recoverRate = 0f;
                if (currentStance == AimStance.PRONE) recoverRate = 20f;
                else if (currentStance == AimStance.CROUCH) recoverRate = 16f;
                else if (currentStance == AimStance.STAND) recoverRate = 12f;
                currentBreath = Math.min(MAX_BREATH, currentBreath + recoverRate * dt);
            }

            float sensitivity = 0.8f;
            aimBaseX += Gdx.input.getDeltaX() * sensitivity;
            aimBaseY -= Gdx.input.getDeltaY() * sensitivity;
            aimBaseX = MathUtils.clamp(aimBaseX, 150f, 650f);
            aimBaseY = MathUtils.clamp(aimBaseY, 0f, 500f);

            float exhaustion = (MAX_BREATH - currentBreath) / MAX_BREATH;
            float breathStability = isHoldingBreath ? 0.45f : (1.4f + exhaustion * 2.5f);

            float stanceStability = 0.8f;
            if (currentStance == AimStance.PRONE) stanceStability = 0.3f;
            else if (currentStance == AimStance.CROUCH) stanceStability = 0.7f;

            boolean anyDroneLockon = false;
            for (CombatDrone cd : drones) {
                if (cd.state == DroneState.LOCKON || cd.state == DroneState.ATTACKING) {
                    anyDroneLockon = true;
                    break;
                }
            }

            float stage3TremorScale = 1.0f;
            float stage3SpeedScale = 1.0f;
            if (drones.size > 0) {
                stage3TremorScale = anyDroneLockon ? 1.5f : 1.2f;
                stage3SpeedScale = anyDroneLockon ? 1.4f : 1.1f;
            }

            float swayMultiplier = (currentStance == AimStance.STAND) ? 1.3f : 1.0f;
            float swaySpeed = (isHoldingBreath ? 0.95f : (2.4f + exhaustion * 3.5f)) * stage3SpeedScale;
            swayTime += dt * swaySpeed * swayMultiplier;

            float frontSightSwayX = MathUtils.sin(swayTime * 1.5f) * (14.0f * stanceStability * breathStability * stage3TremorScale);
            float bodySwayX = frontSightSwayX * 0.85f;
            float bodySwayY = MathUtils.cos(swayTime * 1.8f) * (9.0f * stanceStability * breathStability * stage3TremorScale);

            if (drones.size > 0) {
                float jitterStrength = (isHoldingBreath ? 1.0f : 3.0f) * (anyDroneLockon ? 1.4f : 1.0f);
                float jitterX = MathUtils.random(-jitterStrength, jitterStrength);
                float jitterY = MathUtils.random(-jitterStrength, jitterStrength);
                frontSightSwayX += jitterX;
                bodySwayX += jitterX * 0.5f;
                bodySwayY += jitterY * 0.5f;
            }

            float minVisibleMarginX = 150f;
            float minX = -(GUN_BODY_WIDTH / 2f) + minVisibleMarginX;
            float maxX = 800f + (GUN_BODY_WIDTH / 2f) - minVisibleMarginX;

            float minVisibleMarginY = 120f;
            float minY = -(GUN_BODY_HEIGHT / 2f) + minVisibleMarginY;
            float maxY = 420f;

            gunBodyX = MathUtils.clamp(aimBaseX + bodySwayX, minX, maxX);
            gunBodyY = MathUtils.clamp(aimBaseY + bodyOffsetY + bodySwayY, minY, maxY);

            float holeCenterX = gunBodyX + 30f;
            float holeCenterY = gunBodyY + 150f;

            float targetSightX = holeCenterX + frontSightSwayX + 80f;
            float targetSightY = holeCenterY + 50f;

            float diffX = targetSightX - holeCenterX;
            float diffY = targetSightY - holeCenterY;
            float maxRadius = 35f;

            if (diffX * diffX + diffY * diffY > maxRadius * maxRadius) {
                float angle = MathUtils.atan2(diffY, diffX);
                targetSightX = holeCenterX + MathUtils.cos(angle) * maxRadius;
                targetSightY = holeCenterY + MathUtils.sin(angle) * maxRadius;
            }

            frontSightPos.x = targetSightX;
            frontSightPos.y = targetSightY;
            warpMouseToGunRight();

            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && shootCooldown <= 0f && bulletsLeft > 0 && !isTimeOver && !isPlayerDead) {
                shoot(stanceStability, breathStability);
            }

        } else {
            isHoldingBreath = false;
            aimBaseX = 400f;
            aimBaseY = 300f;
            gunBodyX = 400f;
            gunBodyY = 240f;
            frontSightPos.set(400, 300, 0);

            velocityX = 0;
            boolean isMoving = false;
            boolean isRunning = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            float currentMoveSpeed = isRunning ? RUN_SPEED : MOVE_SPEED;

            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                velocityX -= currentMoveSpeed;
                facingLeft = true;
                isMoving = true;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                velocityX += currentMoveSpeed;
                facingLeft = false;
                isMoving = true;
            }

            if (isMoving) {
                motionState = isRunning ? PlayerMotionState.RUN : PlayerMotionState.WALK;
                float drain = isRunning ? 55f : 25f;
                currentBreath = Math.max(0f, currentBreath - drain * dt);
            } else {
                motionState = PlayerMotionState.IDLE;
                currentBreath = Math.min(MAX_BREATH, currentBreath + 20f * dt);
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && grounded) {
                velocityY = JUMP_SPEED;
                grounded = false;
                currentBreath = Math.max(0f, currentBreath - 12f);
            }

            float currentGravity = (velocityY < 0) ? (BASE_GRAVITY * 2.0f) : BASE_GRAVITY;
            velocityY = Math.max(velocityY - currentGravity * dt, -1200);

            moveX(dt);
            moveY(dt);

            camera.position.x = MathUtils.clamp(player.x + player.width / 2f, 240, worldWidth - 240);
            camera.position.y = 180;
            camera.update();
        }
    }

    private void shoot(float stanceStability, float breathStability) {
        shootCooldown = 0.25f;
        bulletsLeft--;

        float kickY = MathUtils.random(15.0f, 35.0f) * stanceStability;
        float kickX = MathUtils.random(-5.0f, 5.0f) * stanceStability;
        aimBaseY += kickY;
        aimBaseX += kickX;

        float minVisibleMarginX = 150f;
        float minX = -(GUN_BODY_WIDTH / 2f) + minVisibleMarginX;
        float maxX = 800f + (GUN_BODY_WIDTH / 2f) - minVisibleMarginX;
        float minVisibleMarginY = 120f;
        float minY = -(GUN_BODY_HEIGHT / 2f) + minVisibleMarginY;
        float maxY = 420f;

        gunBodyX = MathUtils.clamp(aimBaseX, minX, maxX);
        gunBodyY = MathUtils.clamp(aimBaseY + bodyOffsetY, minY, maxY);
        warpMouseToGunRight();

        if (shotSound != null) {
            shotSound.play(0.85f);
        }

        float tipX = frontSightPos.x + 138f;
        float tipY = frontSightPos.y + (FRONT_SIGHT_H / 2f) + 120f;

        float spreadMultiplier = (drones.size > 0) ? 1.4f : 1.0f;
        float spreadRadius = 0.6f * stanceStability * breathStability * spreadMultiplier;

        float errorX = MathUtils.random(-spreadRadius, spreadRadius);
        float errorY = MathUtils.random(-spreadRadius, spreadRadius);

        float hitX = tipX + errorX;
        float hitY = tipY + errorY;

        float eyeY;
        if (currentStance == AimStance.PRONE) {
            eyeY = player.y + player.height * 0.25f;
        } else if (currentStance == AimStance.CROUCH) {
            eyeY = player.y + player.height * 0.55f;
        } else {
            eyeY = player.y + player.height * 0.85f;
        }

        for (CombatDrone d : drones) {
            if (d.state != DroneState.DESTROYED && d.x + d.width > player.x) {
                float dist = Math.max(60f, d.x - player.x);
                float scale = MathUtils.clamp(450f / dist, 0.4f, 2.6f);
                float dw = d.width * scale;
                float dh = d.height * scale;

                float screenX = 400f - (dw / 2f) + d.getScreenSwayX(scale);
                float screenY = 300f + (d.y - eyeY);

                float hitBoxW = dw * 0.19f;
                float hitBoxH = dh * 0.17f;
                float hitBoxX = screenX + (dw - hitBoxW) / 2f;
                float hitBoxY = screenY + (dh - hitBoxH) / 2f;

                if (hitX >= hitBoxX && hitX <= hitBoxX + hitBoxW &&
                    hitY >= hitBoxY && hitY <= hitBoxY + hitBoxH) {

                    d.state = DroneState.DESTROYED;
                    if (checkAllTargetsGroupingCleared()) {
                        stageClear = true;
                        stopDroneSound();
                        currentStance = AimStance.NONE;
                        isInspectingTarget = true;
                        inspectingTargetIndex = 0;
                    }
                    return;
                }
            }
        }

        Array<Integer> targetIndices = new Array<>();
        for (int i = 0; i < targets.size; i++) {
            if (targets.get(i).x + targets.get(i).width > player.x) {
                targetIndices.add(i);
            }
        }
        targetIndices.sort((i1, i2) -> Float.compare(targets.get(i1).x, targets.get(i2).x));

        for (int idx : targetIndices) {
            Rectangle t = targets.get(idx);
            float dist = Math.max(50f, t.x - player.x);
            float scale = MathUtils.clamp(400f / dist, 0.015f, 2.5f);

            float targetW = Math.max(2f, 56f * scale);
            float targetH = Math.max(4f, t.height * scale);

            float targetScreenX = 400f - (targetW / 2f);
            float targetScreenY = 300f + (t.y - eyeY);

            if (hitX >= targetScreenX && hitX <= targetScreenX + targetW &&
                hitY >= targetScreenY && hitY <= targetScreenY + targetH) {

                float normX = (hitX - targetScreenX) / targetW;
                float normY = (hitY - targetScreenY) / targetH;
                bulletHoles.get(idx).add(new Vector2(normX, normY));

                if (checkAllTargetsGroupingCleared()) {
                    stageClear = true;
                    currentStance = AimStance.NONE;
                    isInspectingTarget = true;
                    inspectingTargetIndex = 0;
                }
                break;
            }
        }

        if (bulletsLeft <= 0 && !stageClear) {
            currentStance = AimStance.NONE;
            isInspectingTarget = true;
            inspectingTargetIndex = 0;
        }
    }

    private int getNearbyTargetIndex() {
        for (int i = 0; i < targets.size; i++) {
            Rectangle t = targets.get(i);
            float dist = Math.abs((player.x + player.width / 2f) - (t.x + t.width / 2f));
            if (dist < 70f && Math.abs(player.y - t.y) < 100f) {
                return i;
            }
        }
        return -1;
    }

    private void moveX(float dt) {
        if (velocityX == 0) return;
        player.x += velocityX * dt;

        for (Rectangle block : blocks) {
            if (!player.overlaps(block)) continue;
            if (velocityX > 0) {
                player.x = block.x - player.width;
            } else if (velocityX < 0) {
                player.x = block.x + block.width;
            }
        }
        player.x = MathUtils.clamp(player.x, 0, worldWidth - player.width);
    }

    private void moveY(float dt) {
        float dy = velocityY * dt;
        player.y += dy;
        grounded = false;

        for (Rectangle block : blocks) {
            if (!player.overlaps(block)) continue;

            if (dy < 0 && (player.y - dy) >= block.y + block.height - 4f) {
                player.y = block.y + block.height;
                velocityY = 0;
                grounded = true;
            } else if (dy > 0 && (player.y - dy + player.height) <= block.y + 4f) {
                player.y = block.y - player.height;
                velocityY = 0;
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
    }

    @Override
    public void dispose() {
        stopDroneSound();

        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();

        if (introLogos != null) {
            for (Texture logo : introLogos) {
                logo.dispose();
            }
        }

        if (introMusic != null) {
            introMusic.dispose();
        }

        if (bgTexture != null) bgTexture.dispose();
        if (stage3BgTexture != null && stage3BgTexture != bgTexture) stage3BgTexture.dispose();
        if (idleSheet != null) idleSheet.dispose();
        if (walkSheet != null) walkSheet.dispose();
        if (runSheet != null) runSheet.dispose();

        blockTexture.dispose();
        targetTexture.dispose();
        if (droneIdleTexture != null && droneIdleTexture != targetTexture) droneIdleTexture.dispose();
        if (shot1Sheet != null) shot1Sheet.dispose();
        if (shot2Sheet != null) shot2Sheet.dispose();
        gunBodyTexture.dispose();
        frontSightTexture.dispose();
        if (shotSound != null) shotSound.dispose();
        if (droneSound != null) droneSound.dispose();
    }
}
