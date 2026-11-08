package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class Main extends ApplicationAdapter {

    private static final float WORLD_WIDTH = 2600;
    private static final float MOVE_SPEED = 260;
    private static final float JUMP_SPEED = 650;
    private static final float BASE_GRAVITY = 1700;

    private static final float SIGHT_FINE_TUNE_SPEED = 140f;
    private static final float GUN_BODY_WIDTH = 1011f;
    private static final float GUN_BODY_HEIGHT = 758f;

    // 가늠쇠 크기
    private static final float FRONT_SIGHT_W = 64f;
    private static final float FRONT_SIGHT_H = 96f;

    // 호흡 파라미터
    private static final float MAX_BREATH = 100f;
    private float currentBreath = MAX_BREATH;
    private boolean isHoldingBreath = false;

    private enum AimStance {
        NONE, CROUCH, PRONE
    }
    private AimStance currentStance = AimStance.NONE;

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    private Texture playerTexture, blockTexture;
    private Texture targetTexture;
    private Texture gunBodyTexture, frontSightTexture;
    private Sound shotSound;

    private OrthographicCamera camera;
    private FitViewport viewport;
    private OrthographicCamera uiCamera;

    private final Rectangle player = new Rectangle(80, 60, 32, 44);
    private final Array<Rectangle> blocks = new Array<>();
    private final Array<Rectangle> targets = new Array<>();

    // 표적지별 탄흔 기록 (0.0~1.0 정규화 좌표 저장)
    private final Array<Array<Vector2>> bulletHoles = new Array<>();

    private float velocityX, velocityY;
    private boolean grounded;
    private boolean gameOver;

    private boolean isInspectingTarget = false;
    private int inspectingTargetIndex = -1;

    private final Vector3 mousePos = new Vector3();

    // 조준 좌표 및 오프셋
    private float aimBaseX = 400f;
    private float aimBaseY = 300f;
    private float swayTime = 0f;
    private float bodyOffsetX = 0f;
    private float bodyOffsetY = -60f;
    private float sightOffsetX = 0f;
    private float sightOffsetY = 0f;

    // 최종 렌더링 좌표
    private float gunBodyX = 400f;
    private float gunBodyY = 240f;
    private final Vector3 frontSightPos = new Vector3(400, 300, 0);

    // 사격 쿨다운 및 반동
    private float shootCooldown = 0f;
    private float recoilOffsetY = 0f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

        playerTexture = new Texture(Gdx.files.internal("player.png"));
        blockTexture = new Texture(Gdx.files.internal("block.png"));
        targetTexture = new Texture(Gdx.files.internal("target.png"));
        gunBodyTexture = new Texture(Gdx.files.internal("gun_body.png"));
        frontSightTexture = new Texture(Gdx.files.internal("front_sight.png"));

        // 사격 사운드 로드 (assets/shot.mp3 파일 필요)
        if (Gdx.files.internal("shot.mp3").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.mp3"));
        } else if (Gdx.files.internal("shot.wav").exists()) {
            shotSound = Gdx.audio.newSound(Gdx.files.internal("shot.wav"));
        }

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 600);
        viewport = new FitViewport(800, 600, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        uiCamera = new OrthographicCamera(800, 600);
        uiCamera.position.set(400, 300, 0);
        uiCamera.update();

        buildRangeMap();
    }

    private void buildRangeMap() {
        blocks.clear();
        targets.clear();
        bulletHoles.clear();

        blocks.add(new Rectangle(0, 0, 1000, 60));
        blocks.add(new Rectangle(1000, 0, 800, 110));
        blocks.add(new Rectangle(1800, 0, 800, 170));

        blocks.add(new Rectangle(-40, 0, 40, 600));
        blocks.add(new Rectangle(2600, 0, 40, 600));

        // 표적지 배치 및 탄흔 배열 초기화
        targets.add(new Rectangle(980, 60, 24, 75));
        bulletHoles.add(new Array<>());

        targets.add(new Rectangle(1780, 110, 24, 75));
        bulletHoles.add(new Array<>());
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

        if (isInspectingTarget) {
            renderTargetInspectionModal();
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

        batch.draw(playerTexture, player.x, player.y, player.width, player.height);

        int nearIdx = getNearbyTargetIndex();
        if (nearIdx != -1 && !isInspectingTarget) {
            font.setColor(Color.BLACK);
            font.draw(batch, "[Press E] Target " + (nearIdx + 1), targets.get(nearIdx).x - 15, targets.get(nearIdx).y + targets.get(nearIdx).height + 25);
        }
        batch.end();
    }

    private void renderFirstPersonView() {
        float eyeY = (currentStance == AimStance.PRONE)
            ? player.y + player.height * 0.25f
            : player.y + player.height * 0.55f;

        Array<Rectangle> forwardBlocks = new Array<>();
        for (Rectangle b : blocks) {
            if (b.x + b.width > player.x) {
                forwardBlocks.add(b);
            }
        }
        forwardBlocks.sort((a, b) -> Float.compare(b.x, a.x));

        // 1. 단면 지형 렌더링
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Rectangle b : forwardBlocks) {
            float dist = Math.max(1f, b.x - player.x);
            float depthAlpha = MathUtils.clamp(1f - (dist / (WORLD_WIDTH - player.x)), 0.25f, 0.95f);
            shapeRenderer.setColor(0.35f * depthAlpha, 0.38f * depthAlpha, 0.42f * depthAlpha, 1f);

            float screenY = 300f + (b.y - eyeY);
            shapeRenderer.rect(0, screenY, 800, b.height);
        }
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        // 2. 전방 표적지 렌더링
        for (Rectangle target : targets) {
            if (target.x + target.width > player.x) {
                float targetScreenY = 300f + (target.y - eyeY);
                float targetWidthInAim = 32f;
                batch.draw(targetTexture, 400 - (targetWidthInAim / 2f), targetScreenY, targetWidthInAim, target.height);
            }
        }

        // 3. W 가늠쇠 (반동 recoilOffsetY 반영)
        float sightDrawX = frontSightPos.x - (FRONT_SIGHT_W / 2f) + 138f;
        float sightDrawY = frontSightPos.y - (FRONT_SIGHT_H / 2f) + 140f + recoilOffsetY;
        batch.draw(
            frontSightTexture,
            sightDrawX,
            sightDrawY,
            FRONT_SIGHT_W,
            FRONT_SIGHT_H
        );

        // 4. 총기 몸체 및 가늠자
        batch.draw(
            gunBodyTexture,
            gunBodyX - (GUN_BODY_WIDTH / 2f),
            gunBodyY - (GUN_BODY_HEIGHT / 2f) + recoilOffsetY,
            GUN_BODY_WIDTH,
            GUN_BODY_HEIGHT
        );

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

        // 표적지에 박힌 탄흔 렌더링
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
        if (gameOver || dt <= 0) return;

        if (shootCooldown > 0) shootCooldown -= dt;
        if (recoilOffsetY > 0) {
            recoilOffsetY = Math.max(0f, recoilOffsetY - 180f * dt);
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

        if (Gdx.input.isKeyPressed(Input.Keys.Z)) {
            currentStance = AimStance.PRONE;
        } else if (Gdx.input.isKeyPressed(Input.Keys.C)) {
            currentStance = AimStance.CROUCH;
        } else {
            currentStance = AimStance.NONE;
        }

        if (currentStance != AimStance.NONE) {
            velocityX = 0;

            if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && currentBreath > 0) {
                isHoldingBreath = true;
                currentBreath = Math.max(0f, currentBreath - 45f * dt);
            } else {
                isHoldingBreath = false;
                float recoverRate = (currentStance == AimStance.PRONE) ? 18f : 12f;
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
            float stanceStability = (currentStance == AimStance.PRONE) ? 0.45f : 1.0f;

            swayTime += dt * (isHoldingBreath ? 0.7f : (1.6f + exhaustion * 3.8f));

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

            // 좌클릭 사격 처리
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && shootCooldown <= 0f) {
                shoot(exhaustion, stanceStability, breathStability);
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
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                velocityX -= MOVE_SPEED;
                isMoving = true;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                velocityX += MOVE_SPEED;
                isMoving = true;
            }

            if (isMoving) {
                currentBreath = Math.max(0f, currentBreath - 35f * dt);
            } else {
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

            camera.position.x = MathUtils.clamp(player.x + player.width / 2f, 400, WORLD_WIDTH - 400);
            camera.position.y = 300;
            camera.update();
        }
    }

    /** 사격 매커니즘: 가늠쇠 상단 중심 조준점 + 오차 반영 및 표적지 명중 판정 */
    private void shoot(float exhaustion, float stanceStability, float breathStability) {
        shootCooldown = 0.25f;
        recoilOffsetY = 24f; // 격발 반동

        // 총소리 재생
        if (shotSound != null) {
            shotSound.play(0.85f);
        }

        // 1. 가늠쇠 상단 중앙 좌표 계산
        // 렌더링 공식: X_draw = frontSightPos.x - (W / 2) + 138, Y_draw = frontSightPos.y - (H / 2) + 140
        // 가늠쇠의 X 중심 = X_draw + W/2 = frontSightPos.x + 138
        // 가늠쇠의 Y 맨 위 = Y_draw + H = frontSightPos.y + (H / 2) + 140
        float tipX = frontSightPos.x + 138f;
        float tipY = frontSightPos.y + (FRONT_SIGHT_H / 2f) + 110f;

        // 2. 오차 범위 계산 (숨을 참으면 오차가 0에 수렴하고, 거칠면 탄착군 분산)
        float spreadRadius = (isHoldingBreath ? 1.5f : 12f) * stanceStability * breathStability;
        float errorX = MathUtils.random(-spreadRadius, spreadRadius);
        float errorY = MathUtils.random(-spreadRadius, spreadRadius);

        float hitX = tipX + errorX;
        float hitY = tipY + errorY;

        // 3. 전방 표적지 피격 검사 (가장 가까운 표적 우선)
        float eyeY = (currentStance == AimStance.PRONE)
            ? player.y + player.height * 0.25f
            : player.y + player.height * 0.55f;

        for (int i = 0; i < targets.size; i++) {
            Rectangle t = targets.get(i);
            if (t.x + t.width > player.x) {
                float targetW = 32f;
                float targetH = t.height;
                float targetScreenX = 400f - (targetW / 2f);
                float targetScreenY = 300f + (t.y - eyeY);

                // 명중 확인
                if (hitX >= targetScreenX && hitX <= targetScreenX + targetW &&
                    hitY >= targetScreenY && hitY <= targetScreenY + targetH) {

                    // 표적지 내 0.0~1.0 정규화 좌표로 탄흔 저장
                    float normX = (hitX - targetScreenX) / targetW;
                    float normY = (hitY - targetScreenY) / targetH;
                    bulletHoles.get(i).add(new Vector2(normX, normY));
                    break;
                }
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
        player.x += velocityX * dt;
        for (Rectangle block : blocks) {
            if (!player.overlaps(block)) continue;
            if (velocityX > 0) player.x = block.x - player.width;
            else if (velocityX < 0) player.x = block.x + block.width;
        }
        player.x = MathUtils.clamp(player.x, 0, WORLD_WIDTH - player.width);
    }

    private void moveY(float dt) {
        float dy = velocityY * dt;
        player.y += dy;
        grounded = false;

        for (Rectangle block : blocks) {
            if (!player.overlaps(block)) continue;
            if (dy < 0) {
                player.y = block.y + block.height;
                velocityY = 0;
                grounded = true;
            } else if (dy > 0) {
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
        playerTexture.dispose();
        blockTexture.dispose();
        targetTexture.dispose();
        gunBodyTexture.dispose();
        frontSightTexture.dispose();
        if (shotSound != null) shotSound.dispose();
    }
}
