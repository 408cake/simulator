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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Main extends ApplicationAdapter {

    // 스테이지 제어 변수
    private String currentStageId = "01_testshoot";
    private String nextStageId = "";
    private String stageTitle = "";
    private String stageHint = "";
    private int hitsRequired = 5;
    private int currentHitCount = 0;
    private boolean stageClear = false;

    // 스테이지별 반동 설정 필드
    private float stageRecoilPower = 24.0f;
    private float stageRecoilRecovery = 180.0f;

    // 클리어 팝업 '다음 스테이지' 버튼 영역 (800x600 UI 기준)
    private final Rectangle nextStageBtnBounds = new Rectangle(300, 220, 200, 50);

    private float worldWidth = 2600;
    private static final float MOVE_SPEED = 200;
    private static final float RUN_SPEED = 320;
    private static final float JUMP_SPEED = 650;
    private static final float BASE_GRAVITY = 1700;

    private static final float SIGHT_FINE_TUNE_SPEED = 140f;
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

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

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

    // 충돌 박스: 실제 몸체 두께 36, 높이 120
    private final Rectangle player = new Rectangle(100, 100, 36, 120);
    private final Array<Rectangle> blocks = new Array<>();
    private final Array<Rectangle> targets = new Array<>();
    private final Array<Array<Vector2>> bulletHoles = new Array<>();

    private float velocityX, velocityY;
    private boolean grounded;

    private boolean isInspectingTarget = false;
    private int inspectingTargetIndex = -1;

    private final Vector3 mousePos = new Vector3();

    private float aimBaseX = 400f;
    private float aimBaseY = 300f;
    private float swayTime = 0f;
    private float bodyOffsetX = 0f;
    private float bodyOffsetY = -60f;
    private float sightOffsetX = 0f;
    private float sightOffsetY = 0f;

    private float gunBodyX = 400f;
    private float gunBodyY = 240f;
    private final Vector3 frontSightPos = new Vector3(400, 300, 0);

    private float shootCooldown = 0f;
    private float recoilOffsetY = 0f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

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

        if (Gdx.files.internal("shot.mp3").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.mp3"));
        } else if (Gdx.files.internal("shot.wav").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.wav"));
        }

        // 2D 횡스크롤 게임 화면 뷰포트 (480x360)
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 360);
        viewport = new FitViewport(480, 360, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        uiCamera = new OrthographicCamera(800, 600);
        uiCamera.position.set(400, 300, 0);
        uiCamera.update();

        // 첫 번째 스테이지 로드
        loadStage("01_testshoot");
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
        hitsRequired = root.getInt("targetHitsRequired", 5);
        worldWidth = root.getFloat("width", 2600f);
        stageHint = root.getString("hint", "");

        stageRecoilPower = root.getFloat("recoilPower", 24.0f);
        stageRecoilRecovery = root.getFloat("recoilRecovery", 180.0f);

        currentHitCount = 0;
        stageClear = false;
        currentStance = AimStance.NONE;
        isInspectingTarget = false;

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
        JsonValue tgList = root.get("targets");
        if (tgList != null) {
            for (JsonValue t = tgList.child; t != null; t = t.next) {
                targets.add(new Rectangle(t.getFloat("x"), t.getFloat("y"), t.getFloat("width", 36f), t.getFloat("height", 110f)));
                bulletHoles.add(new Array<>());
            }
        }
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        update(dt);

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

        if (stageClear) {
            renderClearModal();
        }
    }

    private void renderSideView() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (Rectangle block : blocks) {
            batch.draw(blockTexture, block.x, block.y, block.width, block.height);
        }

        for (Rectangle target : targets) {
            batch.draw(targetTexture, target.x, target.y, target.width, target.height);
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

        // 충돌 박스 중심에 맞춰 스프라이트 렌더링
        float visualW = 90f;
        float visualH = 120f;
        float drawX = player.x + (player.width - visualW) / 2f;
        float drawY = player.y;

        batch.draw(currentFrame, drawX, drawY, visualW, visualH);

        int nearIdx = getNearbyTargetIndex();
        if (nearIdx != -1 && !isInspectingTarget) {
            font.setColor(Color.BLACK);
            font.draw(batch, "[Press E] Target " + (nearIdx + 1), targets.get(nearIdx).x - 15, targets.get(nearIdx).y + targets.get(nearIdx).height + 25);
        }
        batch.end();
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

        Array<Rectangle> forwardTargets = new Array<>();
        for (Rectangle t : targets) {
            if (t.x + t.width > player.x) forwardTargets.add(t);
        }
        forwardTargets.sort((a, b) -> Float.compare(b.x, a.x));

        for (Rectangle target : forwardTargets) {
            float dist = Math.max(50f, target.x - player.x);
            float scale = MathUtils.clamp(400f / dist, 0.35f, 2.2f);
            float scaledW = 48f * scale;
            float scaledH = target.height * scale;
            float targetScreenY = 300f + (target.y - eyeY);
            float targetScreenX = 400f - (scaledW / 2f);

            batch.draw(targetTexture, targetScreenX, targetScreenY, scaledW, scaledH);
        }

        float sightDrawX = frontSightPos.x - (FRONT_SIGHT_W / 2f) + 138f;
        float sightDrawY = frontSightPos.y - (FRONT_SIGHT_H / 2f) + 140f + recoilOffsetY;
        batch.draw(frontSightTexture, sightDrawX, sightDrawY, FRONT_SIGHT_W, FRONT_SIGHT_H);

        batch.draw(
            gunBodyTexture,
            gunBodyX - (GUN_BODY_WIDTH / 2f),
            gunBodyY - (GUN_BODY_HEIGHT / 2f) + recoilOffsetY,
            GUN_BODY_WIDTH,
            GUN_BODY_HEIGHT
        );
        batch.end();
    }

    private void renderHUD() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(0.15f, 0.15f, 0.15f, 1f);
        font.draw(batch, "[" + stageTitle + "] 명중: " + currentHitCount + " / " + hitsRequired, 25, 575);
        if (!stageHint.isEmpty()) {
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
        String breathText = isHoldingBreath ? "BREATH [HOLDING - FAST DRAIN]" : "BREATH [HOLD SPACE]";
        font.draw(batch, breathText, 32, 65);
        batch.end();
    }

    private void renderClearModal() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
        shapeRenderer.rect(0, 0, 800, 600);

        shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapeRenderer.rect(250, 200, 300, 220);

        shapeRenderer.setColor(0.2f, 0.6f, 0.2f, 1f);
        shapeRenderer.rect(nextStageBtnBounds.x, nextStageBtnBounds.y, nextStageBtnBounds.width, nextStageBtnBounds.height);
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(Color.BLACK);
        font.draw(batch, "★ STAGE CLEAR! ★", 335, 380);
        font.draw(batch, "목표 달성: 5발 적중 완료", 330, 335);

        font.setColor(Color.WHITE);
        String btnText = nextStageId.isEmpty() ? "GAME COMPLETE" : "NEXT STAGE";
        font.draw(batch, btnText, 350, 252);
        batch.end();
    }

    private void renderTargetInspectionModal() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
        shapeRenderer.rect(0, 0, 800, 600);
        shapeRenderer.end();

        float modalTargetX = 275f;
        float modalTargetY = 120f;
        float modalTargetW = 250f;
        float modalTargetH = 360f;

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        batch.draw(targetTexture, modalTargetX, modalTargetY, modalTargetW, modalTargetH);
        font.setColor(Color.WHITE);
        font.draw(batch, "TARGET #" + (inspectingTargetIndex + 1) + " VIEW", 355, 520);
        font.draw(batch, "[Press E or ESC to Close]", 310, 80);
        batch.end();

        if (inspectingTargetIndex >= 0 && inspectingTargetIndex < bulletHoles.size) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 0.95f);
            for (Vector2 hole : bulletHoles.get(inspectingTargetIndex)) {
                float hx = modalTargetX + hole.x * modalTargetW;
                float hy = modalTargetY + hole.y * modalTargetH;
                shapeRenderer.circle(hx, hy, 4f);
            }
            shapeRenderer.end();
        }
    }

    private void update(float dt) {
        if (dt <= 0) return;

        if (stageClear) {
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
                if (nextStageBtnBounds.contains(mousePos.x, mousePos.y)) {
                    if (!nextStageId.isEmpty()) {
                        loadStage(nextStageId);
                    }
                }
            }
            return;
        }

        stateTime += dt;
        if (shootCooldown > 0) shootCooldown -= dt;
        if (recoilOffsetY > 0) {
            recoilOffsetY = Math.max(0f, recoilOffsetY - stageRecoilRecovery * dt);
        }

        if (isInspectingTarget) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                isInspectingTarget = false;
            }
            return;
        }

        int nearIdx = getNearbyTargetIndex();
        if (nearIdx != -1 && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            isInspectingTarget = true;
            inspectingTargetIndex = nearIdx;
            return;
        }

        // 토글 조준
        if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            currentStance = (currentStance == AimStance.PRONE) ? AimStance.NONE : AimStance.PRONE;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            currentStance = (currentStance == AimStance.CROUCH) ? AimStance.NONE : AimStance.CROUCH;
        }

        // 우클릭 서서쏴
        if (currentStance != AimStance.PRONE && currentStance != AimStance.CROUCH) {
            if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                currentStance = AimStance.STAND;
            } else {
                currentStance = AimStance.NONE;
            }
        }

        if (currentStance != AimStance.NONE) {
            velocityX = 0;
            motionState = PlayerMotionState.IDLE;

            if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && currentBreath > 0) {
                isHoldingBreath = true;
                currentBreath = Math.max(0f, currentBreath - 45f * dt);
            } else {
                isHoldingBreath = false;
                float recoverRate = 12f;
                if (currentStance == AimStance.PRONE) recoverRate = 20f;
                else if (currentStance == AimStance.CROUCH) recoverRate = 16f;
                else if (currentStance == AimStance.STAND) recoverRate = 12f;
                currentBreath = Math.min(MAX_BREATH, currentBreath + recoverRate * dt);
            }

            mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            uiCamera.unproject(mousePos, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());

            aimBaseX = MathUtils.lerp(aimBaseX, mousePos.x, 0.18f);
            aimBaseY = MathUtils.lerp(aimBaseY, mousePos.y, 0.18f);

            if (Gdx.input.isKeyPressed(Input.Keys.A)) bodyOffsetX -= SIGHT_FINE_TUNE_SPEED * dt;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) bodyOffsetX += SIGHT_FINE_TUNE_SPEED * dt;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) bodyOffsetY += SIGHT_FINE_TUNE_SPEED * dt;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) bodyOffsetY -= SIGHT_FINE_TUNE_SPEED * dt;

            float exhaustion = (MAX_BREATH - currentBreath) / MAX_BREATH;
            float breathStability = isHoldingBreath ? 0.15f : (1.0f + exhaustion * 3.0f);

            float stanceStability = 1.6f;
            if (currentStance == AimStance.PRONE) {
                stanceStability = 0.45f;
            } else if (currentStance == AimStance.CROUCH) {
                stanceStability = 1.0f;
            }

            float swayMultiplier = (currentStance == AimStance.STAND) ? 1.4f : 1.0f;
            swayTime += dt * (isHoldingBreath ? 0.7f : (1.6f + exhaustion * 3.8f)) * swayMultiplier;

            float frontSightSwayX = MathUtils.sin(swayTime * 1.5f) * (6.5f * stanceStability * breathStability);
            float bodySwayX = frontSightSwayX * 0.85f;
            float bodySwayY = MathUtils.cos(swayTime * 1.8f) * (4.0f * stanceStability * breathStability);

            float minVisibleMarginX = 150f;
            float minX = -(GUN_BODY_WIDTH / 2f) + minVisibleMarginX;
            float maxX = 800f + (GUN_BODY_WIDTH / 2f) - minVisibleMarginX;

            float minVisibleMarginY = 120f;
            float minY = -(GUN_BODY_HEIGHT / 2f) + minVisibleMarginY;
            float maxY = 420f;

            gunBodyX = MathUtils.clamp(aimBaseX + bodyOffsetX + bodySwayX, minX, maxX);
            gunBodyY = MathUtils.clamp(aimBaseY + bodyOffsetY + bodySwayY, minY, maxY);

            float holeCenterX = gunBodyX + 30f;
            float holeCenterY = gunBodyY + 150f;

            float targetSightX = holeCenterX + sightOffsetX + frontSightSwayX + 80f;
            float targetSightY = holeCenterY + sightOffsetY + 50f;

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

            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && shootCooldown <= 0f) {
                shoot(stanceStability, breathStability);
            }

        } else {
            isHoldingBreath = false;
            aimBaseX = 400f;
            aimBaseY = 300f;
            bodyOffsetX = 0f;
            bodyOffsetY = -60f;
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
        recoilOffsetY = stageRecoilPower * (stanceStability * 0.8f);

        if (shotSound != null) {
            shotSound.play(0.85f);
        }

        float tipX = frontSightPos.x + 138f;
        float tipY = frontSightPos.y + (FRONT_SIGHT_H / 2f) + 110f;

        float spreadRadius = (isHoldingBreath ? 1.5f : 12f) * stanceStability * breathStability;
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
            float scale = MathUtils.clamp(400f / dist, 0.35f, 2.2f);

            float targetW = 32f * scale;
            float targetH = t.height * scale;

            float targetScreenX = 400f - (targetW / 2f);
            float targetScreenY = 300f + (t.y - eyeY);

            if (hitX >= targetScreenX && hitX <= targetScreenX + targetW &&
                hitY >= targetScreenY && hitY <= targetScreenY + targetH) {

                float normX = (hitX - targetScreenX) / targetW;
                float normY = (hitY - targetScreenY) / targetH;
                bulletHoles.get(idx).add(new Vector2(normX, normY));

                currentHitCount++;
                if (currentHitCount >= hitsRequired) {
                    stageClear = true;
                    currentStance = AimStance.NONE;
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

        if (idleSheet != null) idleSheet.dispose();
        if (walkSheet != null) walkSheet.dispose();
        if (runSheet != null) runSheet.dispose();

        blockTexture.dispose();
        targetTexture.dispose();
        gunBodyTexture.dispose();
        frontSightTexture.dispose();
        if (shotSound != null) shotSound.dispose();
    }
}
