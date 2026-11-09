package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
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
    private static final float FADE_IN_TIME = 0.8f;
    private static final float HOLD_TIME = 1.2f;
    private static final float FADE_OUT_TIME = 0.8f;
    private float logoAlpha = 0f;

    private String currentStageId = "01_testshoot";
    private String nextStageId = "";
    private String stageTitle = "";
    private String stageHint = "";
    private boolean stageClear = false;
    private boolean isPlayerDead = false;

    private static final float STAGE_TIME_LIMIT = 75.0f;
    private float stageTimer = STAGE_TIME_LIMIT;
    private boolean isTimeOver = false;

    private final IntArray targetsRequiredHits = new IntArray();
    private int maxAmmo = 18;
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
    private Texture idleSheet, walkSheet, runSheet;
    private Animation<TextureRegion> idleAnim;
    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> runAnim;
    private float stateTime = 0f;
    private boolean facingLeft = false;

    private Texture blockTexture, targetTexture;
    private Texture gunBodyTexture, frontSightTexture;
    private Sound shotSound;

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
        if (introLogos.size == 0) {
            gameState = GameState.GAMEPLAY;
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("neodgm.ttf"));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = 18;
        parameter.color = Color.WHITE;
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "가각간갇갈갉갊감갑값갓갔강갖갗같갚갛개객갠갤갬갭갯갰갱갸갹갼걀걈걉걋걍걔걘걜거걱건걷걸걺검겁것겄겅겁게겐겔겜겝겟겠겡겨격겪견겯결겸겹겻겼경곁계곈곌곕곗고곡곤곧골곪곬곯곰곱곳공곶과곽관괄괆괌괍왓광괘괜괄괨괩괬굉괴괵괸괼굄굅귓굉교굔굘굠굡굣구국군굳굴굵굶굻굼굽굿궁궂궈궉권궐궤궤궤귀귁귄귈귐귑귓규균귤그극근귿글긁금급긋긍긔기긱긴긷길긺김깁깃깅깆깊"
            + "사격표적확인조준호흡거리발자세앉아쏴누워쏴완료합격실패재시작다음단계점수적중잔여탄약검사탄착군안내이동남음풀고세요닫기다시시도영점기준원밀집형성이상원격모니터실시간관측원거리전환이전다음초과시간제한드론경고사망격추피격"
            + "★/[]: #1234567890.<>";

        font = generator.generateFont(parameter);
        generator.dispose();

        bgTexture = new Texture(Gdx.files.internal("01_background.png"));

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

    public void triggerPlayerDeath() {
        if (!stageClear && !isPlayerDead) {
            isPlayerDead = true;
            currentStance = AimStance.NONE;
            isInspectingTarget = true;
        }
    }

    public void loadStage(String stageId) {
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

        maxAmmo = root.getInt("maxAmmo", 18);
        bulletsLeft = maxAmmo;
        stageClear = false;
        isPlayerDead = false;

        stageTimer = STAGE_TIME_LIMIT;
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
        if (drList != null) {
            for (JsonValue d = drList.child; d != null; d = d.next) {
                drones.add(new CombatDrone(
                    d.getFloat("x"), d.getFloat("y"),
                    d.getFloat("minX", d.getFloat("x") - 250f),
                    d.getFloat("maxX", d.getFloat("x") + 250f),
                    d.getFloat("minY", 160f),
                    d.getFloat("maxY", 310f)
                ));
            }
        } else if (stageId.contains("03") || stageId.contains("drone")) {
            drones.add(new CombatDrone(1100, 240, 700, 1500, 160, 310));
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

        update(dt);

        ScreenUtils.clear(0.85f, 0.9f, 0.95f, 1f);
        viewport.apply();

        if (currentStance != AimStance.NONE) {
            renderFirstPersonView();
            if (targets.size >= 3) {
                renderLiveSpottingMonitor();
            }
            renderBreathUI();
        } else {
            renderSideView();
        }

        renderHUD();

        if (isInspectingTarget) {
            renderTargetInspectionModal();
        }
    }

    private void updateIntro(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            nextLogoOrStartGame();
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

    private void nextLogoOrStartGame() {
        currentLogoIndex++;
        if (currentLogoIndex >= introLogos.size) {
            gameState = GameState.GAMEPLAY;
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

        float bgHeight = 360f;
        float bgScale = bgHeight / bgTexture.getHeight();
        float bgWidth = bgTexture.getWidth() * bgScale;
        for (float x = 0; x < worldWidth; x += bgWidth) {
            batch.draw(bgTexture, x, 0, bgWidth, bgHeight);
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
        } else if (player.x < 300f && !isInspectingTarget && targets.size > 0) {
            font.setColor(new Color(0.1f, 0.4f, 0.8f, 1f));
            font.draw(batch, "[E 키] 사격 모니터 점검", player.x - 20, player.y + player.height + 25);
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
            float scale = MathUtils.clamp(400f / dist, 0.3f, 2.5f);
            float scaledW = 56f * scale;
            float scaledH = target.height * scale;
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
                float scale = MathUtils.clamp(400f / dist, 0.3f, 2.5f);
                float scaledW = 56f * scale;
                float scaledH = target.height * scale;
                float targetScreenY = 300f + (target.y - eyeY);
                float targetScreenX = 400f - (scaledW / 2f);

                if (idx < bulletHoles.size) {
                    for (Vector2 hole : bulletHoles.get(idx)) {
                        float hx = targetScreenX + hole.x * scaledW;
                        float hy = targetScreenY + hole.y * scaledH;
                        float holeR = Math.max(2.0f, 3.5f * scale);

                        shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1f);
                        shapeRenderer.circle(hx, hy, holeR + 1.2f);

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

    private void renderLiveSpottingMonitor() {
        int remoteIdx = 2;
        if (targets.size <= remoteIdx) return;

        float monX = 640f;
        float monY = 410f;
        float monW = 140f;
        float monH = 170f;

        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.08f, 0.12f, 0.85f);
        shapeRenderer.rect(monX - 6, monY - 26, monW + 12, monH + 34);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        shapeRenderer.setColor(0.2f, 0.7f, 1.0f, 0.9f);
        shapeRenderer.rect(monX - 6, monY - 26, monW + 12, monH + 34);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        batch.draw(targetTexture, monX, monY, monW, monH);
        font.setColor(new Color(0.3f, 0.85f, 1.0f, 1f));
        font.draw(batch, "[T3 관측경]", monX + 18, monY + monH + 20);
        batch.end();

        Array<Vector2> holes = bulletHoles.get(remoteIdx);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < holes.size; i++) {
            Vector2 h = holes.get(i);
            float hx = monX + h.x * monW;
            float hy = monY + h.y * monH;

            shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f);
            shapeRenderer.circle(hx, hy, 4.5f);
            shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
            shapeRenderer.circle(hx, hy, 2.5f);
        }
        shapeRenderer.end();
    }

    private void renderHUD() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(0.15f, 0.15f, 0.15f, 1f);

        String timeStr = String.format("%.1f", Math.max(0f, stageTimer));
        Color timeColor = (stageTimer <= 5.0f) ? new Color(0.85f, 0.1f, 0.1f, 1f) : new Color(0.15f, 0.15f, 0.15f, 1f);

        if (drones.size > 0) {
            int aliveDrones = 0;
            for (CombatDrone d : drones) if (d.state != DroneState.DESTROYED) aliveDrones++;
            font.draw(batch, "[" + stageTitle + "] 잔여탄: " + bulletsLeft + " / " + maxAmmo +
                "  |  적 드론: " + aliveDrones + "기", 25, 575);
        } else {
            int t1 = bulletHoles.size > 0 ? bulletHoles.get(0).size : 0;
            int t2 = bulletHoles.size > 1 ? bulletHoles.get(1).size : 0;
            int t3 = bulletHoles.size > 2 ? bulletHoles.get(2).size : 0;
            font.draw(batch, "[" + stageTitle + "] 잔여탄: " + bulletsLeft + " / " + maxAmmo +
                "  |  T1: " + t1 + "/5  T2: " + t2 + "/5  T3: " + t3 + "/3", 25, 575);
        }

        font.setColor(timeColor);
        font.draw(batch, "남은 시간: " + timeStr + "초", 650, 575);

        if (isPlayerDead) {
            font.setColor(new Color(1f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 적 드론의 공격에 피격당했습니다! 사망 ★", 25, 550);
        } else if (stageClear) {
            font.setColor(new Color(0.1f, 0.65f, 0.2f, 1f));
            font.draw(batch, "★ 임무 완수! [E]를 눌러 결과를 확인하세요. ★", 25, 550);
        } else if (isTimeOver) {
            font.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 시간 초과! [E]를 눌러 다시 시도하세요. ★", 25, 550);
        } else if (bulletsLeft <= 0) {
            font.setColor(new Color(0.85f, 0.1f, 0.1f, 1f));
            font.draw(batch, "★ 잔여 탄약 소진! [E]를 눌러 상태를 확인하세요. ★", 25, 550);
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
        shapeRenderer.setColor(0f, 0f, 0f, 0.85f);
        shapeRenderer.rect(0, 0, 800, 600);

        if (drones.size > 0) {
            if (isPlayerDead) {
                shapeRenderer.setColor(0.85f, 0.2f, 0.2f, 1f);
                shapeRenderer.rect(singleCenterBtn.x, singleCenterBtn.y, singleCenterBtn.width, singleCenterBtn.height);
            } else if (stageClear) {
                shapeRenderer.setColor(0.2f, 0.7f, 0.3f, 1f);
                shapeRenderer.rect(singleCenterBtn.x, singleCenterBtn.y, singleCenterBtn.width, singleCenterBtn.height);
            }
            shapeRenderer.end();

            batch.setProjectionMatrix(uiCamera.combined);
            batch.begin();
            if (isPlayerDead) {
                font.setColor(1f, 0.25f, 0.25f, 1f);
                font.draw(batch, "★ 적 드론 피격으로 인한 전사 (KIA) ★", 280, 360);
                font.setColor(Color.WHITE);
                font.draw(batch, "적 드론의 공격 전에 먼저 조준하여 격추해야 합니다.", 225, 300);
                font.draw(batch, "다시 시도", singleCenterBtn.x + 36, singleCenterBtn.y + 26);
            } else if (stageClear) {
                font.setColor(0.3f, 1f, 0.4f, 1f);
                font.draw(batch, "★ 적 드론 격추 완료! MISSION CLEAR ★", 260, 360);
                font.setColor(Color.WHITE);
                font.draw(batch, "위협을 성공적으로 무력화했습니다.", 285, 300);
                String nextText = nextStageId.isEmpty() ? "게임 완료" : "다음 단계";
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
                shapeRenderer.setColor(0.2f, 0.65f, 0.25f, 1f);
                shapeRenderer.rect(modalNextBtn.x, modalNextBtn.y, modalNextBtn.width, modalNextBtn.height);
            }
        }
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        batch.draw(targetTexture, modalTargetX, modalTargetY, modalTargetW, modalTargetH);

        font.setColor(Color.WHITE);
        font.draw(batch, "표적 #" + (inspectingTargetIndex + 1) + " 탄착군 원격 검사 (←, → 키로 전환)", 250, 565);

        boolean curSuccess = isTargetGroupingSuccess(inspectingTargetIndex);
        int reqHits = (inspectingTargetIndex < targetsRequiredHits.size) ? targetsRequiredHits.get(inspectingTargetIndex) : 5;
        int curHoles = (inspectingTargetIndex < bulletHoles.size) ? bulletHoles.get(inspectingTargetIndex).size : 0;

        if (curSuccess) {
            font.setColor(0.3f, 1f, 0.4f, 1f);
            font.draw(batch, "[표적 #" + (inspectingTargetIndex + 1) + " 탄착군 통과 (" + curHoles + "/" + reqHits + ")]", 310, 150);
        } else {
            font.setColor(1f, 0.35f, 0.35f, 1f);
            font.draw(batch, "[표적 #" + (inspectingTargetIndex + 1) + " 미달성 (" + curHoles + "/" + reqHits + " 요구)]", 310, 150);
        }

        if (isSessionOver) {
            if (stageClear && !isTimeOver) {
                font.setColor(0.3f, 1f, 0.4f, 1f);
                font.draw(batch, "★ 전체 표적 임무 완료! 합격 ★", 315, 60);
            } else if (isTimeOver) {
                font.setColor(1f, 0.3f, 0.3f, 1f);
                font.draw(batch, "제한 시간 초과: 다시 시도하세요.", 280, 60);
            } else {
                font.setColor(1f, 0.3f, 0.3f, 1f);
                font.draw(batch, "전체 기준 미달: 재사격이 필요합니다.", 275, 60);
            }

            font.setColor(Color.WHITE);
            font.draw(batch, "다시 시도", modalRestartBtn.x + 36, modalRestartBtn.y + 26);
            if (stageClear && !isTimeOver) {
                String nextText = nextStageId.isEmpty() ? "완료" : "다음 단계";
                font.draw(batch, nextText, modalNextBtn.x + 36, modalNextBtn.y + 26);
            }
        } else {
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
                shapeRenderer.setColor(curSuccess ? new Color(0.2f, 1f, 0.3f, 0.85f) : new Color(1f, 0.85f, 0.2f, 0.75f));
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
                    shapeRenderer.setColor(1f, 0.3f, 0.3f, 1f);
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

        for (CombatDrone drone : drones) {
            drone.update(dt, player.x, player.y, this);
        }

        if (!stageClear && !isInspectingTarget && !isTimeOver && !isPlayerDead) {
            stageTimer -= dt;
            if (stageTimer <= 0f) {
                stageTimer = 0f;
                isTimeOver = true;
                currentStance = AimStance.NONE;
            }
        }

        stateTime += dt;
        if (shootCooldown > 0) shootCooldown -= dt;

        if (isInspectingTarget) {
            if (drones.size > 0) {
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                    uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());

                    if (isPlayerDead && singleCenterBtn.contains(mousePos.x, mousePos.y)) {
                        loadStage(currentStageId);
                        return;
                    } else if (stageClear && singleCenterBtn.contains(mousePos.x, mousePos.y)) {
                        if (!nextStageId.isEmpty()) loadStage(nextStageId);
                        else loadStage(currentStageId);
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
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                isInspectingTarget = false;
            }
            return;
        }

        int nearIdx = getNearbyTargetIndex();
        boolean canOpenInspection = (nearIdx != -1) || (player.x < 300f && targets.size > 0) || isTimeOver;

        if (canOpenInspection && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            isInspectingTarget = true;
            if (nearIdx != -1) inspectingTargetIndex = nearIdx;
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

            // 3단계 드론 스테이지 떨림 배율: 락온 시 극도의 공포감 주입
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
                stage3TremorScale = anyDroneLockon ? 2.8f : 1.6f;
                stage3SpeedScale = anyDroneLockon ? 2.4f : 1.4f;
            }

            float swayMultiplier = (currentStance == AimStance.STAND) ? 1.3f : 1.0f;
            float swaySpeed = (isHoldingBreath ? 0.95f : (2.4f + exhaustion * 3.5f)) * stage3SpeedScale;
            swayTime += dt * swaySpeed * swayMultiplier;

            // 기본 사인파 호흡 흔들림
            float frontSightSwayX = MathUtils.sin(swayTime * 1.5f) * (14.0f * stanceStability * breathStability * stage3TremorScale);
            float bodySwayX = frontSightSwayX * 0.85f;
            float bodySwayY = MathUtils.cos(swayTime * 1.8f) * (9.0f * stanceStability * breathStability * stage3TremorScale);

            // 3단계 전용: 손이 사시나무 떨리듯 떨리는 고주파 지터(Jitter) 추가
            if (drones.size > 0) {
                // 숨 참을 때 1.0px, 안 참을 때 3.0px 정도로 줄여서 사격 가능하게 조정
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

        // 3단계 떨림에 비례하여 탄퍼짐 증폭
        float spreadMultiplier = (drones.size > 0) ? 1.8f : 1.0f;
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

        // 1. 드론 피격 검사 (축소된 히트박스 영역)
        for (CombatDrone d : drones) {
            if (d.state != DroneState.DESTROYED && d.x + d.width > player.x) {
                float dist = Math.max(60f, d.x - player.x);
                float scale = MathUtils.clamp(450f / dist, 0.4f, 2.6f);
                float dw = d.width * scale;
                float dh = d.height * scale;

                float screenX = 400f - (dw / 2f) + d.getScreenSwayX(scale);
                float screenY = 300f + (d.y - eyeY);

                float hitBoxW = dw * 0.60f;
                float hitBoxH = dh * 0.65f;
                float hitBoxX = screenX + (dw - hitBoxW) / 2f;
                float hitBoxY = screenY + (dh - hitBoxH) / 2f;

                if (hitX >= hitBoxX && hitX <= hitBoxX + hitBoxW &&
                    hitY >= hitBoxY && hitY <= hitBoxY + hitBoxH) {

                    d.state = DroneState.DESTROYED;
                    if (checkAllTargetsGroupingCleared()) {
                        stageClear = true;
                        currentStance = AimStance.NONE;
                        isInspectingTarget = true;
                    }
                    return;
                }
            }
        }

        // 2. 일반 지상 표적지 피격 검사 (1, 2단계)
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
            float scale = MathUtils.clamp(400f / dist, 0.3f, 2.5f);

            float targetW = 56f * scale;
            float targetH = t.height * scale;

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
                }
                break;
            }
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
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();

        if (introLogos != null) {
            for (Texture logo : introLogos) {
                logo.dispose();
            }
        }

        if (bgTexture != null) bgTexture.dispose();
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
    }
}
