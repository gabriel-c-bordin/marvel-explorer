package com.marvelexplorer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Self-contained mini-games with dedicated, animated training screens. */
public class GamesActivity extends Activity {
    private static final int NAVY = Color.rgb(7, 17, 30);
    private static final int PANEL = Color.rgb(16, 34, 56);
    private static final int CYAN = Color.rgb(69, 230, 255);
    private static final int RED = Color.rgb(230, 36, 53);
    private static final int INK = Color.rgb(239, 247, 255);
    private static final int MUTED = Color.rgb(153, 176, 194);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout stage;
    private SenseArena senseArena;
    private final List<String> quizReview = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BottomNavigation.prepareWindow(this);
        setContentView(R.layout.activity_games);
        stage = findViewById(R.id.gameStage);
        showLobby();
        BottomNavigation.attach(this, 2);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onPause() {
        if (senseArena != null) senseArena.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (senseArena != null && !senseArena.userPaused) senseArena.resume();
    }

    private void showLobby() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(16), dp(18), dp(22));

        LinearLayout briefing = panel();
        briefing.setBackground(background(0xFF102A43, 0xFF0B1C2D, 20, 0xFF28445C));
        TextView emblem = label("✦", 38, CYAN, true);
        emblem.setGravity(Gravity.CENTER);
        briefing.addView(emblem, new LinearLayout.LayoutParams(-1, dp(54)));
        briefing.addView(label("PROTOCOLO DE TREINO", 11, CYAN, true));
        TextView intro = label("Escolha uma missão e mostre do que é capaz.", 15, INK, true);
        intro.setPadding(0, dp(7), 0, 0);
        briefing.addView(intro);
        content.addView(briefing, bottomMargin(briefing, 16));

        content.addView(sectionLabel("DESAFIOS DISPONÍVEIS"));
        addGameCard(content, "01", "Memória dos heróis", "Três níveis: 4, 6 ou 8 pares. Treine sua memória.", "▦", CYAN, this::startMemory);
        addGameCard(content, "02", "Quiz Marvel", "Rodadas variadas, respostas comentadas e progresso.", "?", RED, this::startQuiz);
        addGameCard(content, "03", "Sentido-Aranha", "Perceba o perigo e desvie dos obstáculos em três pistas.", "ϟ", 0xFFB79AFF, this::startReflex);
        addGameCard(content, "04", "Batalha de personagens", "Escolha seu herói e vença um duelo por turnos.", "⚔", 0xFFFFB454, this::chooseBattleHero);
        addGameCard(content, "05", "Protocolo de Tony Stark", "Memorize a sequência do reator e impeça uma sobrecarga.", "ARC", CYAN, this::startStarkHack);
        addGameCard(content, "06", "Labirinto de Kamar-Taj", "Encontre a rota segura pelo portal antes que as rupturas consumam suas chances.", "✦", 0xFFFFB454, this::startPortalRun);
        content.addView(label("Dica da central: os melhores agentes treinam todos os dias.", 12, MUTED, false));
        scroll.addView(content);
        showScreen(scroll);
    }

    private void addGameCard(LinearLayout parent, String number, String title, String description,
                             String icon, int accent, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(background(0xFF102238, 0xFF102238, 18, 0xFF263F56));
        TextView glyph = label(icon, 27, accent, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(background(0xFF1A334B, 0xFF1A334B, 15, 0xFF294861));
        card.addView(glyph, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout copy = column();
        copy.setPadding(dp(13), 0, dp(8), 0);
        copy.addView(label(number + "  ·  " + title, 16, INK, true));
        TextView desc = label(description, 12, MUTED, false);
        desc.setPadding(0, dp(4), 0, 0);
        copy.addView(desc);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(label("›", 28, accent, true));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> { animatePress(card); action.run(); });
        parent.addView(card, bottomMargin(card, 11));
        card.setAlpha(0f);
        card.setTranslationY(dp(14));
        card.animate().alpha(1f).translationY(0).setDuration(360).setStartDelay((Integer.parseInt(number) - 1) * 75L)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void startStarkHack() {
        LinearLayout screen = gameScreen("PROTOCOLO DE TONY STARK", "Memorize os sinais e estabilize o reator ARC", "ARC", CYAN);
        TextView status = label("SEQUÊNCIA 1 / 6  ·  FALHAS 0 / 3", 14, CYAN, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        TextView signal = label("", 20, INK, true);
        signal.setGravity(Gravity.CENTER);
        signal.setPadding(0, dp(18), 0, dp(18));
        screen.addView(signal);
        TextView feedback = label("Observe o padrão do núcleo. Toque em iniciar quando estiver pronto.", 14, MUTED, false);
        feedback.setPadding(0, dp(8), 0, dp(8));
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(feedback);

        String[] modules = {"ARC", "ARMADURA", "REPULSOR", "NEXUS"};
        int[] accents = {CYAN, 0xFFFFC56E, RED, 0xFFB79AFF};
        Button[] keys = new Button[modules.length];
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (int i = 0; i < modules.length; i++) {
            final int key = i;
            Button button = gameButton(modules[i]);
            button.setContentDescription("Módulo " + modules[i]);
            button.setBackground(background(0xFF102238, 0xFF102238, 16, accents[i]));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(70);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(8), dp(4), dp(8));
            grid.addView(button, params);
            keys[i] = button;
        }
        screen.addView(grid);

        int[] round = {1}, failures = {0}, cursor = {0};
        boolean[] active = {false}, ended = {false};
        List<Integer> pattern = new ArrayList<>();
        Button toggle = gameButton("INICIAR SEQUÊNCIA");
        LinearLayout.LayoutParams toggleParams = bottomMargin(toggle, 10);
        toggleParams.topMargin = dp(10);
        screen.addView(toggle, toggleParams);

        Runnable makePattern = () -> {
            pattern.clear();
            int length = round[0] + 2;
            for (int i = 0; i < length; i++) pattern.add(random.nextInt(modules.length));
            cursor[0] = 0;
            active[0] = false;
            signal.setText(patternText(pattern, modules));
            toggle.setText("MEMORIZE E TOQUE PARA COMEÇAR");
            toggle.setEnabled(true);
            feedback.setText("A sequência tem " + length + " sinais. Você pode iniciar quando estiver pronto.");
            for (Button key : keys) key.setEnabled(false);
        };

        toggle.setOnClickListener(v -> {
            if (ended[0]) return;
            if (!active[0]) {
                active[0] = true;
                cursor[0] = 0;
                signal.setText("SINAL OCULTO");
                feedback.setText("Repita os módulos na ordem correta.");
                toggle.setText("PADRÃO EM ANDAMENTO");
                toggle.setEnabled(false);
                for (Button key : keys) key.setEnabled(true);
            }
        });

        for (int i = 0; i < keys.length; i++) {
            final int key = i;
            keys[i].setOnClickListener(v -> {
                if (!active[0] || ended[0]) return;
                animatePress(v);
                if (pattern.get(cursor[0]) != key) {
                    failures[0]++;
                    cursor[0] = 0;
                    status.setText("SEQUÊNCIA " + round[0] + " / 6  ·  FALHAS " + failures[0] + " / 3");
                    if (failures[0] >= 3) {
                        ended[0] = true;
                        active[0] = false;
                        signal.setText("NÚCLEO INSTÁVEL");
                        feedback.setText("O reator sobrecarregou. Tente novamente e memorize o padrão.");
                        addAction(screen, "REINICIAR PROTOCOLO", CYAN, this::startStarkHack);
                        return;
                    }
                    feedback.setText("Sinal incorreto. O padrão foi reiniciado; tente mais uma vez.");
                    return;
                }
                cursor[0]++;
                if (cursor[0] == pattern.size()) {
                    active[0] = false;
                    round[0]++;
                    if (round[0] > 6) {
                        ended[0] = true;
                        signal.setText("REATOR ESTÁVEL");
                        feedback.setText("Seis sequências concluídas. Você salvou a armadura e o núcleo ARC!");
                        status.setText("PROTOCOLO CONCLUÍDO  ·  FALHAS " + failures[0]);
                        toggle.setVisibility(View.GONE);
                        addAction(screen, "JOGAR NOVAMENTE", CYAN, this::startStarkHack);
                    } else {
                        status.setText("SEQUÊNCIA " + round[0] + " / 6  ·  FALHAS " + failures[0] + " / 3");
                        feedback.setText("Sequência correta! O reator acelerou.");
                        makePattern.run();
                    }
                } else {
                    feedback.setText("Sinal " + cursor[0] + " de " + pattern.size() + " confirmado.");
                }
            });
        }
        makePattern.run();
        showScreen(screen);
    }

    private String patternText(List<Integer> pattern, String[] modules) {
        StringBuilder text = new StringBuilder();
        for (int key : pattern) {
            if (text.length() > 0) text.append("  ·  ");
            text.append(modules[key]);
        }
        return text.toString();
    }

    private void startPortalRun() {
        showPortalLevel(0, 3);
    }

    private void showPortalLevel(int level, int lives) {
        int[][] routes = {
                {12, 8, 4, 5, 1, 2, 3},
                {12, 13, 9, 5, 6, 2, 3},
                {12, 8, 9, 10, 6, 2, 3}
        };
        boolean[] safe = new boolean[16];
        for (int cell : routes[level]) safe[cell] = true;
        int[] player = {12};
        int[] remainingLives = {lives};
        boolean[] finished = {false};

        LinearLayout screen = gameScreen("LABIRINTO DE KAMAR-TAJ", "Nível " + (level + 1) + " de 3 · alcance o portal dourado", "✦", 0xFFFFB454);
        TextView status = label("TENTATIVAS " + lives + "  ·  PASSOS 0", 14, CYAN, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        TextView hint = label("Comece no canto inferior esquerdo. Avance uma célula por vez; rupturas consomem uma tentativa.", 14, MUTED, false);
        hint.setPadding(0, dp(8), 0, dp(12));
        screen.addView(hint);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        Button[] cells = new Button[16];
        for (int i = 0; i < cells.length; i++) {
            final int cellIndex = i;
            Button cell = gameButton(i == 12 ? "VOCÊ" : (i == 3 ? "PORTAL" : "·"));
            cell.setTextSize(i == 12 || i == 3 ? 11 : 22);
            cell.setPadding(dp(2), dp(2), dp(2), dp(2));
            cell.setContentDescription(i == 12 ? "Sua posição" : (i == 3 ? "Portal de saída" : "Célula " + (i + 1)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(64);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(5), dp(8), dp(5), dp(8));
            grid.addView(cell, params);
            cells[i] = cell;
        }
        screen.addView(grid, bottomMargin(grid, 10));

        for (int i = 0; i < cells.length; i++) {
            final int destination = i;
            cells[i].setOnClickListener(v -> {
                if (finished[0]) return;
                int rowDistance = Math.abs(destination / 4 - player[0] / 4);
                int columnDistance = Math.abs(destination % 4 - player[0] % 4);
                if (rowDistance + columnDistance != 1) {
                    hint.setText("O portal só aceita um passo para cima, baixo, esquerda ou direita.");
                    return;
                }
                if (!safe[destination]) {
                    remainingLives[0]--;
                    cells[destination].setText("RUPTURA");
                    cells[destination].setTextSize(9);
                    cells[destination].setTextColor(RED);
                    cells[destination].setEnabled(false);
                    cells[destination].setContentDescription("Ruptura dimensional, caminho bloqueado");
                    cells[player[0]].setText("·");
                    player[0] = 12;
                    cells[player[0]].setText("VOCÊ");
                    hint.setText("Ruptura! A rota reiniciou. Escolha outra direção.");
                    status.setText("TENTATIVAS " + remainingLives[0] + "  ·  PASSOS PERDIDOS");
                    if (remainingLives[0] <= 0) {
                        finished[0] = true;
                        screen.addView(resultBanner("PORTAL FECHADO", "As rupturas venceram desta vez."), bottomMargin(screen, 8));
                        addAction(screen, "TENTAR NOVAMENTE", 0xFFFFB454, this::startPortalRun);
                    }
                    return;
                }
                cells[player[0]].setText("✦");
                cells[player[0]].setTextColor(0xFFFFD36C);
                player[0] = destination;
                cells[destination].setText(destination == 3 ? "SAIR" : "VOCÊ");
                cells[destination].setTextColor(INK);
                status.setText("TENTATIVAS " + remainingLives[0] + "  ·  POSIÇÃO " + (destination + 1));
                hint.setText("Rota estável. Continue avançando pelo labirinto.");
                if (destination == 3) {
                    finished[0] = true;
                    screen.addView(resultBanner("PORTAL ALCANÇADO", "Rota " + (level + 1) + " concluída com " + remainingLives[0] + " tentativas."), bottomMargin(screen, 8));
                    if (level < 2) addAction(screen, "ABRIR PRÓXIMO PORTAL", 0xFFFFB454, () -> showPortalLevel(level + 1, remainingLives[0]));
                    addAction(screen, "REJOGAR NÍVEL", 0xFFFFB454, () -> showPortalLevel(level, 3));
                }
            });
        }
        showScreen(screen);
    }

    private void startMemory() {
        LinearLayout screen = gameScreen("MEMÓRIA DOS HERÓIS", "Escolha o tamanho do desafio", "▦", CYAN);
        screen.addView(label("O título Mestre da Memória exige uma partida sem erros. Não há limite de tempo.", 14, MUTED, false));
        addAction(screen, "RECRUTA · 4 PARES", CYAN, () -> startMemory(4));
        addAction(screen, "AGENTE · 6 PARES", CYAN, () -> startMemory(6));
        addAction(screen, "VINGADOR · 8 PARES", CYAN, () -> startMemory(8));
        showScreen(screen);
    }

    private static final class MemoryCard {
        final String name;
        final int characterId;
        MemoryCard(String name, int characterId) {
            this.name = name;
            this.characterId = characterId;
        }
    }

    private void startMemory(int pairs) {
        String[][] characters = {
                {"HOMEM-ARANHA", "SPIDER-MAN"}, {"HOMEM DE FERRO", "IRON MAN"},
                {"CAPITÃO AMÉRICA", "CAPTAIN AMERICA"}, {"HULK", "THE HULK"},
                {"PANTERA NEGRA", "BLACK PANTHER"}, {"VIÚVA NEGRA", "BLACK WIDOW"},
                {"GAVIÃO ARQUEIRO", "HAWKEYE"}, {"DOUTOR ESTRANHO", "DOCTOR STRANGE"}
        };
        List<Integer> characterIds = new ArrayList<>();
        for (int i = 0; i < characters.length; i++) characterIds.add(i);
        Collections.shuffle(characterIds, random);
        List<MemoryCard> deck = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            int characterId = characterIds.get(i);
            deck.add(new MemoryCard(characters[characterId][0], characterId));
            deck.add(new MemoryCard(characters[characterId][1], characterId));
        }
        Collections.shuffle(deck, random);
        boolean[] matched = new boolean[deck.size()];
        int[] first = {-1}, turns = {0}, found = {0};
        boolean[] locked = {false};
        LinearLayout screen = gameScreen("MEMÓRIA DOS HERÓIS", pairs + " pares · ligue os nomes em português e inglês", "▦", CYAN);
        TextView status = label("PARES 0 / " + pairs, 13, CYAN, true);
        status.setPadding(0, dp(12), 0, dp(12));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        TextView attempts = label("TENTATIVAS 0", 13, MUTED, true);
        attempts.setPadding(0, 0, 0, dp(8));
        screen.addView(attempts);
        ProgressBar progress = progressBar(pairs, CYAN);
        screen.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        GridLayout grid = new GridLayout(this);
        int columns = getResources().getConfiguration().screenWidthDp < 360 ? 3 : 4;
        grid.setColumnCount(columns);
        Button[] tiles = new Button[deck.size()];
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            Button tile = gameButton("?");
            tile.setTextSize(11);
            tile.setPadding(0, 0, 0, 0);
            tile.setContentDescription("Carta " + (i + 1) + ", oculta");
            tiles[i] = tile;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(68); lp.columnSpec = GridLayout.spec(i % columns, 1f);
            lp.setMargins(dp(4), dp(8), dp(4), dp(8));
            grid.addView(tile, lp);
            tile.setOnClickListener(v -> {
                if (locked[0] || matched[index] || first[0] == index) return;
                flip(tile, deck.get(index).name);
                tile.setTextSize(deck.get(index).name.length() > 12 ? 9 : 11);
                tile.setContentDescription("Carta " + (index + 1) + ": " + deck.get(index).name);
                if (first[0] < 0) { first[0] = index; return; }
                int prior = first[0]; first[0] = -1; turns[0]++;
                if (deck.get(prior).characterId == deck.get(index).characterId) {
                    matched[prior] = matched[index] = true;
                    found[0]++;
                    progress.setProgress(found[0]);
                    for (int n : new int[]{prior, index}) {
                        tiles[n].setTextColor(CYAN);
                        tiles[n].setBackground(background(0xFF123E45, 0xFF123E45, 15, CYAN));
                        tiles[n].setEnabled(false);
                    }
                    if (found[0] == pairs) {
                        if (turns[0] == pairs) GamificationManager.unlockTitle(this, "memory");
                        screen.addView(resultBanner("MISSÃO CUMPRIDA", pairs + " pares em " + turns[0] + " tentativas."));
                        addAction(screen, "REPETIR NÍVEL", CYAN, () -> startMemory(pairs));
                        if (pairs < 8) addAction(screen, "PRÓXIMO NÍVEL", CYAN, () -> startMemory(pairs + 2));
                        addAction(screen, "ESCOLHER NÍVEL", CYAN, this::startMemory);
                    }
                } else {
                    locked[0] = true;
                    handler.postDelayed(() -> {
                        if (!isActive(screen)) return;
                        flip(tiles[prior], "?"); flip(tile, "?");
                        tiles[prior].setContentDescription("Carta " + (prior + 1) + ", oculta");
                        tile.setContentDescription("Carta " + (index + 1) + ", oculta");
                        locked[0] = false;
                    }, 1000);
                }
                status.setText("PARES " + found[0] + " / " + pairs);
                attempts.setText("TENTATIVAS " + turns[0] + (locked[0] ? " · Observe as cartas" : " · Par encontrado!"));
            });
        }
        screen.addView(grid);
        screen.addView(label("Encontre o mesmo personagem: um nome em português e o outro em inglês.", 13, MUTED, false));
        showScreen(screen);
    }

    private static final class BattleHero {
        final int id, accent, maxHp, attack;
        final String name, glyph, role, moveOne, moveTwo, debuff, special, ultimate;
        BattleHero(int id, int accent, int maxHp, int attack, String name, String glyph, String role,
                   String moveOne, String moveTwo, String debuff, String special, String ultimate) {
            this.id = id; this.accent = accent; this.maxHp = maxHp; this.attack = attack;
            this.name = name; this.glyph = glyph; this.role = role; this.moveOne = moveOne;
            this.moveTwo = moveTwo; this.debuff = debuff; this.special = special; this.ultimate = ultimate;
        }
    }

    private static final class BattleMatch {
        final BattleHero player, rival;
        int playerHp, rivalHp, playerUlt, rivalUlt, playerShield, rivalShield;
        int playerWeak, rivalWeak, playerBuff, rivalBuff;
        boolean playerDodge, rivalDodge, playerCrit, rivalCrit, rivalStunned, playerStunned;
        boolean busy, finished;
        String lastImpact = "";
        BattleMatch(BattleHero player, BattleHero rival) {
            this.player = player; this.rival = rival;
            playerHp = player.maxHp; rivalHp = rival.maxHp;
        }
    }

    private BattleHero[] battleRoster() {
        return new BattleHero[]{
                new BattleHero(0, 0xFFFF5C76, 118, 25, "Homem-Aranha", "🕷️", "Ágil",
                        "Teia Impactante", "Chute Giratório", "Teia Restritora", "Sentido-Aranha", "Balanço Máximo"),
                new BattleHero(1, 0xFF63C5FF, 126, 26, "Homem de Ferro", "🤖", "Tecnologia",
                        "Repulsor", "Míssil Teleguiado", "Pulso EMP", "Escudo de Energia", "Canhão Unibeam"),
                new BattleHero(2, 0xFF64A8FF, 142, 23, "Capitão América", "🛡️", "Defensor",
                        "Arremesso de Escudo", "Investida do Primeiro Vingador", "Ricochete Tático", "Formação de Combate", "Avante, Vingadores!"),
                new BattleHero(3, 0xFFB79AFF, 128, 29, "Thor", "⚡", "Cósmico",
                        "Mjölnir", "Raio de Asgard", "Tempestade Atordoante", "Bênção de Asgard", "Fúria do Trovão"),
                new BattleHero(4, 0xFF65E58D, 170, 25, "Hulk", "💚", "Força bruta",
                        "Soco Esmagador", "Salto do Hulk", "Abalo Sísmico", "Ficar Mais Forte", "Esmagamento Gama"),
                new BattleHero(5, 0xFFBA8CFF, 130, 27, "Pantera Negra", "🐾", "Estratégia",
                        "Garras de Vibranium", "Investida da Pantera", "Marca do Caçador", "Foco de Wakanda", "Legado de Wakanda"),
                new BattleHero(6, 0xFFFFA85B, 122, 28, "Doutor Estranho", "✨", "Místico",
                        "Chicote Místico", "Disco dos Serafins", "Laço de Cyttorak", "Dobra Temporal", "Portal Dimensional"),
                new BattleHero(7, 0xFFFFC56E, 150, 27, "Wolverine", "🐺", "Regeneração",
                        "Garras de Adamantium", "Corte Berserker", "Ameaça Selvagem", "Fator de Cura", "Fúria Berserker"),
                new BattleHero(8, 0xFFFFD36C, 132, 30, "Capitã Marvel", "🌟", "Energia",
                        "Rajada Fotônica", "Impacto Binário", "Supressão Cósmica", "Forma Binária", "Explosão de Fótons"),
                new BattleHero(9, 0xFFFF6C91, 124, 29, "Feiticeira Escarlate", "🔮", "Caos",
                        "Rajada Hex", "Lança do Caos", "Enfraquecimento Mental", "Drenagem do Caos", "Magia do Caos")
        };
    }

    private void chooseBattleHero() {
        BattleHero[] roster = battleRoster();
        LinearLayout screen = gameScreen("SELEÇÃO DE HERÓI", "Escolha um dos 10 combatentes disponíveis", "⚔", 0xFFFFB454);
        TextView prompt = label("SELECIONE SEU PERSONAGEM", 13, CYAN, true);
        prompt.setPadding(0, dp(3), 0, dp(10));
        screen.addView(prompt);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (BattleHero hero : roster) {
            Button card = gameButton(hero.glyph + "\n" + hero.name + "\n" + hero.role);
            card.setTextSize(14);
            card.setTextColor(hero.accent);
            card.setBackground(background(0xFF102238, 0xFF102238, 16, hero.accent));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = dp(104);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(8), dp(4), dp(8));
            grid.addView(card, params);
            card.setOnClickListener(v -> startBattle(hero));
        }
        screen.addView(grid);
        showScreen(screen);
    }

    private void startBattle(BattleHero player) {
        BattleHero[] roster = battleRoster();
        List<BattleHero> rivals = new ArrayList<>();
        for (BattleHero candidate : roster) if (candidate.id != player.id) rivals.add(candidate);
        Collections.shuffle(rivals);
        BattleMatch match = new BattleMatch(player, rivals.get(0));
        showBattle(match);
    }

    private void showBattle(BattleMatch match) {
        LinearLayout screen = gameScreen("ARENA DE COMBATE", "Duelo por turnos · prepare sua estratégia", "⚔", 0xFFFFB454);
        LinearLayout arena = new LinearLayout(this);
        arena.setOrientation(LinearLayout.VERTICAL);
        arena.setPadding(dp(13), dp(12), dp(13), dp(12));
        arena.setBackground(background(0xFF142D46, 0xFF0C1B2B, 20, 0xFF38566B));

        LinearLayout fighters = new LinearLayout(this);
        LinearLayout playerColumn = column(), rivalColumn = column();
        playerColumn.setGravity(Gravity.CENTER); rivalColumn.setGravity(Gravity.CENTER);
        View playerSprite = sprite(match.player), rivalSprite = sprite(match.rival);
        playerColumn.addView(label("VOCÊ", 11, CYAN, true));
        playerColumn.addView(playerSprite, new LinearLayout.LayoutParams(-1, dp(128)));
        playerColumn.addView(heroStatus(match.player, true, match), new LinearLayout.LayoutParams(-1, -2));
        rivalColumn.addView(label("RIVAL", 11, RED, true));
        rivalColumn.addView(rivalSprite, new LinearLayout.LayoutParams(-1, dp(128)));
        rivalColumn.addView(heroStatus(match.rival, false, match), new LinearLayout.LayoutParams(-1, -2));
        fighters.addView(playerColumn, new LinearLayout.LayoutParams(0, -2, 1));
        TextView versus = label("VS", 12, MUTED, true);
        versus.setGravity(Gravity.CENTER);
        fighters.addView(versus, new LinearLayout.LayoutParams(dp(28), -1));
        fighters.addView(rivalColumn, new LinearLayout.LayoutParams(0, -2, 1));
        arena.addView(fighters);
        screen.addView(arena, bottomMargin(arena, 10));

        TextView turnMessage = label("Seu turno. Escolha uma habilidade.", 13, CYAN, true);
        turnMessage.setGravity(Gravity.CENTER);
        turnMessage.setPadding(0, dp(4), 0, dp(7));
        turnMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(turnMessage);

        LinearLayout ultimateBox = panel();
        ultimateBox.setOrientation(LinearLayout.HORIZONTAL);
        ultimateBox.setGravity(Gravity.CENTER_VERTICAL);
        ultimateBox.setPadding(dp(12), dp(9), dp(12), dp(9));
        TextView ultimateLabel = label("GOLPE ESPECIAL", 11, 0xFFFFD36C, true);
        ultimateLabel.setTag("battle_ultimate_label");
        ultimateBox.addView(ultimateLabel, new LinearLayout.LayoutParams(dp(66), -2));
        ProgressBar ultimateBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        ultimateBar.setMax(100);
        ultimateBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFB454));
        ultimateBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF263E54));
        ultimateBox.addView(ultimateBar, new LinearLayout.LayoutParams(0, dp(10), 1));
        Button ultimateButton = gameButton("LIBERAR");
        ultimateButton.setTextSize(11);
        ultimateButton.setMinHeight(dp(48));
        ultimateButton.setEnabled(false);
        ultimateButton.setAlpha(0.48f);
        LinearLayout.LayoutParams ultimateButtonParams = new LinearLayout.LayoutParams(dp(94), dp(48));
        ultimateButtonParams.setMargins(dp(9), 0, 0, 0);
        ultimateBox.addView(ultimateButton, ultimateButtonParams);
        screen.addView(ultimateBox, bottomMargin(ultimateBox, 9));

        GridLayout moves = new GridLayout(this);
        moves.setColumnCount(2);
        Button[] moveButtons = new Button[4];
        String[] moveNames = {match.player.moveOne, match.player.moveTwo, match.player.debuff, match.player.special};
        String[] kinds = {"DANO", "DANO", "DEBUFF", "ESPECIAL"};
        for (int i = 0; i < moveButtons.length; i++) {
            final int moveIndex = i;
            Button move = gameButton(kinds[i] + "\n" + moveNames[i]);
            move.setTextSize(13);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = dp(76);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(8), dp(4), dp(8));
            moves.addView(move, params);
            moveButtons[i] = move;
            move.setOnClickListener(v -> playMove(match, moveIndex, turnMessage, rivalSprite, playerSprite,
                    ultimateBar, ultimateButton, moveButtons));
        }
        screen.addView(moves);
        ultimateButton.setOnClickListener(v -> playMove(match, 4, turnMessage, rivalSprite, playerSprite,
                ultimateBar, ultimateButton, moveButtons));
        showScreen(screen);
        updateBattleUi(match, turnMessage, ultimateBar, ultimateButton, moveButtons);
    }

    private LinearLayout heroStatus(BattleHero hero, boolean isPlayer, BattleMatch match) {
        LinearLayout status = column();
        status.setPadding(dp(10), dp(8), dp(10), dp(8));
        status.setBackground(background(0xFF0B1A29, 0xFF0B1A29, 12, 0xFF38566B));
        TextView name = label(hero.name.toUpperCase(java.util.Locale.ROOT), 12, INK, true);
        status.addView(name);
        TextView level = label("NV. " + GamificationManager.level(this), 10, MUTED, false);
        level.setPadding(0, dp(2), 0, dp(3));
        status.addView(level);
        ProgressBar hp = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        hp.setMax(hero.maxHp);
        hp.setProgress(isPlayer ? match.playerHp : match.rivalHp);
        hp.setProgressTintList(android.content.res.ColorStateList.valueOf(isPlayer ? 0xFF45E69C : 0xFFFF657D));
        hp.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF3A2635));
        status.addView(hp, new LinearLayout.LayoutParams(-1, dp(8)));
        TextView hpText = label("VIDA  " + (isPlayer ? match.playerHp : match.rivalHp) + " / " + hero.maxHp,
                10, MUTED, false);
        hpText.setTag(isPlayer ? "battle_player_hp" : "battle_rival_hp");
        hp.setTag(isPlayer ? "battle_player_bar" : "battle_rival_bar");
        hpText.setPadding(0, dp(3), 0, 0);
        status.addView(hpText);
        return status;
    }

    private View sprite(BattleHero hero) {
        AvatarView avatar = new AvatarView(hero.accent, hero.id);
        avatar.setContentDescription("Avatar de " + hero.name);
        return avatar;
    }

    /** Original geometric training avatar; no images, logos or external assets. */
    private class AvatarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent, variant;
        AvatarView(int accent, int variant) {
            super(GamesActivity.this);
            this.accent = accent; this.variant = variant;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 100f, getHeight() / 132f);
            canvas.save();
            canvas.translate((getWidth() - 100 * scale) / 2, (getHeight() - 132 * scale) / 2);
            canvas.scale(scale, scale);
            drawAvatar(canvas, paint, accent, variant);
            canvas.restore();
        }
    }

    private void drawAvatar(Canvas c, Paint p, int accent, int variant) {
        p.setColor(0xFF29435B); c.drawOval(12, 119, 88, 129, p);
        p.setColor(accent);
        float shoulder = variant == 4 ? 22 : 30;
        c.drawRoundRect(shoulder, 43, 100 - shoulder, 91, 12, 12, p);
        p.setStrokeWidth(13); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(shoulder, 53, 16, 82, p); c.drawLine(100 - shoulder, 53, 84, 82, p);
        p.setColor(0xFF7D9DBB);
        c.drawLine(40, 90, 34, 115, p); c.drawLine(60, 90, 66, 115, p);
        p.setColor(accent); c.drawCircle(50, 25, 19, p);
        p.setColor(INK); c.drawRoundRect(36, 22, 46, 27, 2, 2, p); c.drawRoundRect(54, 22, 64, 27, 2, 2, p);
        p.setColor(NAVY); c.drawRoundRect(43, 33, 57, 36, 2, 2, p);
        p.setColor(INK); c.drawCircle(50, 63, 7, p);
        // Distinct accessories: round guard, energy staff or shoulder plates.
        if (variant % 3 == 0) {
            p.setColor(0xFF7D9DBB); c.drawCircle(17, 80, 14, p);
            p.setColor(accent); c.drawCircle(17, 80, 9, p);
        } else if (variant % 3 == 1) {
            p.setColor(CYAN); p.setStrokeWidth(4); c.drawLine(86, 48, 86, 107, p);
            c.drawCircle(86, 46, 7, p);
        } else {
            p.setColor(0xFFFFD36C); c.drawRoundRect(22, 45, 37, 53, 3, 3, p);
            c.drawRoundRect(63, 45, 78, 53, 3, 3, p);
        }
    }

    private void playMove(BattleMatch match, int move, TextView message, View rivalSprite, View playerSprite,
                          ProgressBar ultimateBar, Button ultimateButton, Button[] moveButtons) {
        if (match.busy || match.finished || (move == 4 && match.playerUlt < 100)) return;
        match.busy = true;
        match.lastImpact = "";
        animatePress(playerSprite);
        if (match.playerStunned) {
            match.playerStunned = false;
            message.setText("Você ficou preso no tempo e perdeu o turno!");
            updateBattleUi(match, message, ultimateBar, ultimateButton, moveButtons);
            handler.postDelayed(() -> enemyTurn(match, message, rivalSprite, playerSprite,
                    ultimateBar, ultimateButton, moveButtons), 650);
            return;
        }
        if (move < 2) {
            int power = move == 0 ? match.player.attack : match.player.attack + 10;
            int damage = power + random.nextInt(9) - 2;
            if (match.playerWeak > 0) damage = Math.round(damage * 0.7f);
            if (match.playerBuff > 0) { damage = Math.round(damage * 1.35f); match.playerBuff--; }
            if (match.playerCrit) { damage = Math.round(damage * 1.5f); match.playerCrit = false; message.setText("ACERTO CRÍTICO!"); }
            else message.setText(match.player.name + " usou " + (move == 0 ? match.player.moveOne : match.player.moveTwo) + "!");
            damageTarget(match, true, damage);
            match.playerUlt = Math.min(100, match.playerUlt + 28);
            animateHit(rivalSprite, true);
        } else if (move == 2) {
            match.rivalWeak = 2;
            match.playerUlt = Math.min(100, match.playerUlt + 18);
            message.setText(match.player.debuff + "! O ataque rival foi reduzido.");
            animateHit(rivalSprite, true);
        } else if (move == 3) {
            useUnique(match, true, message, rivalSprite);
            match.playerUlt = Math.min(100, match.playerUlt + 18);
        } else {
            int damage = match.player.attack * 3 + random.nextInt(14);
            if (match.playerBuff > 0) { damage = Math.round(damage * 1.35f); match.playerBuff = 0; }
            match.playerUlt = 0;
            damageTarget(match, true, damage);
            message.setText(match.player.ultimate + "!");
            animateHit(rivalSprite, true);
        }
        if (!match.lastImpact.isEmpty()) message.append("\n" + match.lastImpact);
        if (match.playerWeak > 0) match.playerWeak--;
        updateBattleUi(match, message, ultimateBar, ultimateButton, moveButtons);
        if (match.rivalHp <= 0) { finishBattle(match, true, message, moveButtons, ultimateButton); return; }
        handler.postDelayed(() -> enemyTurn(match, message, rivalSprite, playerSprite,
                ultimateBar, ultimateButton, moveButtons), 650);
    }

    private void enemyTurn(BattleMatch match, TextView message, View rivalSprite, View playerSprite,
                           ProgressBar ultimateBar, Button ultimateButton, Button[] moveButtons) {
        if (isFinishing() || isDestroyed() || !message.isAttachedToWindow() || match.finished) return;
        match.lastImpact = "";
        animatePress(rivalSprite);
        if (match.rivalStunned) {
            match.rivalStunned = false;
            message.setText(match.rival.name + " perdeu o turno!");
        } else {
            int move = match.rivalUlt >= 100 ? 4 : random.nextInt(4);
            if (move < 2) {
                int damage = match.rival.attack + (move == 1 ? 8 : 0) + random.nextInt(8) - 2;
                if (match.rivalWeak > 0) damage = Math.round(damage * 0.7f);
                if (match.rivalBuff > 0) { damage = Math.round(damage * 1.35f); match.rivalBuff--; }
                if (match.rivalCrit) { damage = Math.round(damage * 1.5f); match.rivalCrit = false; }
                damageTarget(match, false, damage);
                match.rivalUlt = Math.min(100, match.rivalUlt + 28);
                message.setText(match.rival.name + " usou " + (move == 0 ? match.rival.moveOne : match.rival.moveTwo) + "!");
                animateHit(playerSprite, false);
            } else if (move == 2) {
                match.playerWeak = 2;
                match.rivalUlt = Math.min(100, match.rivalUlt + 18);
                message.setText(match.rival.debuff + "! Seu ataque foi reduzido.");
            } else if (move == 3) {
                useUnique(match, false, message, playerSprite);
                match.rivalUlt = Math.min(100, match.rivalUlt + 18);
            } else {
                int damage = match.rival.attack * 3 + random.nextInt(14);
                if (match.rivalWeak > 0) damage = Math.round(damage * 0.7f);
                if (match.rivalBuff > 0) { damage = Math.round(damage * 1.35f); match.rivalBuff = 0; }
                match.rivalUlt = 0;
                damageTarget(match, false, damage);
                message.setText(match.rival.ultimate + "!");
                animateHit(playerSprite, false);
            }
        }
        if (!match.lastImpact.isEmpty()) message.append("\n" + match.lastImpact);
        if (match.rivalWeak > 0) match.rivalWeak--;
        if (match.playerHp <= 0) {
            updateBattleUi(match, message, ultimateBar, ultimateButton, moveButtons);
            finishBattle(match, false, message, moveButtons, ultimateButton);
            return;
        }
        match.busy = false;
        updateBattleUi(match, message, ultimateBar, ultimateButton, moveButtons);
    }

    private void damageTarget(BattleMatch match, boolean targetRival, int rawDamage) {
        boolean dodge = targetRival ? match.rivalDodge : match.playerDodge;
        if (dodge) {
            if (targetRival) match.rivalDodge = false; else match.playerDodge = false;
            match.lastImpact = "ESQUIVA! Nenhuma vida perdida.";
            return;
        }
        int shield = targetRival ? match.rivalShield : match.playerShield;
        int absorbed = Math.min(shield, rawDamage);
        int damage = rawDamage - absorbed;
        int hpBefore = targetRival ? match.rivalHp : match.playerHp;
        match.lastImpact = "−" + Math.min(hpBefore, damage) + " de vida" + (absorbed > 0 ? " · o escudo absorveu " + absorbed : "");
        if (targetRival) {
            match.rivalShield -= absorbed;
            match.rivalHp = Math.max(0, match.rivalHp - damage);
            match.rivalUlt = Math.min(100, match.rivalUlt + 16);
        } else {
            match.playerShield -= absorbed;
            match.playerHp = Math.max(0, match.playerHp - damage);
            match.playerUlt = Math.min(100, match.playerUlt + 16);
        }
    }

    private void useUnique(BattleMatch match, boolean playerSide, TextView message, View targetSprite) {
        BattleHero hero = playerSide ? match.player : match.rival;
        switch (hero.id) {
            case 0:
                if (playerSide) match.playerDodge = true; else match.rivalDodge = true;
                message.setText(hero.special + "! O próximo golpe será esquivado."); break;
            case 1:
                if (playerSide) match.playerShield += 38; else match.rivalShield += 38;
                message.setText(hero.special + "! Escudo de energia ativado."); break;
            case 2:
                heal(match, playerSide, 32);
                message.setText(hero.special + "! Recuperou 32 pontos de vida."); break;
            case 3:
                if (playerSide) match.playerBuff = 2; else match.rivalBuff = 2;
                message.setText(hero.special + "! Próximos ataques fortalecidos."); break;
            case 4:
                heal(match, playerSide, 24);
                if (playerSide) match.playerBuff = 1; else match.rivalBuff = 1;
                message.setText(hero.special + "! Hulk recupera vida e fica mais forte."); break;
            case 5:
                if (playerSide) match.playerCrit = true; else match.rivalCrit = true;
                message.setText(hero.special + "! Próximo ataque será crítico."); break;
            case 6:
                if (playerSide) match.rivalStunned = true; else match.playerStunned = true;
                message.setText(hero.special + "! O tempo prende o rival."); break;
            case 7:
                heal(match, playerSide, 42);
                message.setText(hero.special + "! O fator de cura restaurou 42 pontos de vida."); break;
            case 8:
                if (playerSide) { match.playerShield += 24; match.playerBuff = 1; }
                else { match.rivalShield += 24; match.rivalBuff = 1; }
                message.setText(hero.special + "! Escudo e poder binário ativados."); break;
            default:
                int drained = Math.min(26, playerSide ? match.rivalHp : match.playerHp);
                damageTarget(match, !playerSide, drained);
                heal(match, playerSide, drained);
                message.setText(hero.special + "! Drenou " + drained + " pontos de vida do rival.");
                animateHit(targetSprite, playerSide);
        }
    }

    private void heal(BattleMatch match, boolean playerSide, int amount) {
        if (playerSide) match.playerHp = Math.min(match.player.maxHp, match.playerHp + amount);
        else match.rivalHp = Math.min(match.rival.maxHp, match.rivalHp + amount);
    }

    private void updateBattleUi(BattleMatch match, TextView message, ProgressBar ultimateBar,
                                Button ultimateButton, Button[] moveButtons) {
        message.setTextColor(match.busy ? 0xFFFFD36C : CYAN);
        ViewGroup root = (ViewGroup) stage.getChildAt(0);
        View playerHpView = root.findViewWithTag("battle_player_hp");
        View rivalHpView = root.findViewWithTag("battle_rival_hp");
        View playerBarView = root.findViewWithTag("battle_player_bar");
        View rivalBarView = root.findViewWithTag("battle_rival_bar");
        if (playerHpView instanceof TextView) ((TextView) playerHpView).setText("VIDA  " + match.playerHp + " / " + match.player.maxHp + battleEffects(match, true));
        if (rivalHpView instanceof TextView) ((TextView) rivalHpView).setText("VIDA  " + match.rivalHp + " / " + match.rival.maxHp + battleEffects(match, false));
        if (playerBarView instanceof ProgressBar) ((ProgressBar) playerBarView).setProgress(match.playerHp);
        if (rivalBarView instanceof ProgressBar) ((ProgressBar) rivalBarView).setProgress(match.rivalHp);
        ultimateBar.setProgress(match.playerUlt);
        View ultimateLabel = root.findViewWithTag("battle_ultimate_label");
        if (ultimateLabel instanceof TextView) ((TextView) ultimateLabel).setText("ESPECIAL " + match.playerUlt + "%");
        ultimateButton.setEnabled(match.playerUlt >= 100 && !match.busy && !match.finished);
        ultimateButton.setText(match.playerUlt >= 100 ? "USAR" : "CARREGANDO");
        ultimateButton.setAlpha(match.playerUlt >= 100 && !match.busy ? 1f : 0.48f);
        for (Button button : moveButtons) button.setEnabled(!match.busy && !match.finished);
    }

    private void finishBattle(BattleMatch match, boolean victory, TextView message,
                              Button[] moveButtons, Button ultimateButton) {
        match.finished = true; match.busy = false;
        for (Button button : moveButtons) button.setEnabled(false);
        ultimateButton.setEnabled(false);
        message.setText(victory ? "VITÓRIA! O rival foi derrotado." : "FIM DE COMBATE. Seu herói precisa de descanso.");
        message.setTextColor(victory ? 0xFF45E69C : RED);
        if (victory) GamificationManager.completeAndNotify(this, GamificationManager.BATTLE);
        LinearLayout actions = column();
        Button rematch = gameButton(victory ? "NOVO DESAFIO" : "TENTAR NOVAMENTE");
        LinearLayout.LayoutParams rematchParams = bottomMargin(rematch, 10);
        rematchParams.topMargin = dp(10);
        actions.addView(rematch, rematchParams);
        rematch.setOnClickListener(v -> chooseBattleHero());
        Button lobby = gameButton("VOLTAR À SALA DE TREINO");
        LinearLayout.LayoutParams lobbyParams = bottomMargin(lobby, 10);
        lobbyParams.topMargin = dp(10);
        actions.addView(lobby, lobbyParams);
        lobby.setOnClickListener(v -> showLobby());
        LinearLayout gameContent = (LinearLayout) ((ScrollView) stage.getChildAt(0)).getChildAt(0);
        gameContent.addView(actions, bottomMargin(actions, 4));
        popIn(actions);
    }

    private void animateHit(View sprite, boolean toLeft) {
        float direction = toLeft ? -dp(14) : dp(14);
        sprite.animate().translationX(direction).alpha(0.55f).setDuration(110).withEndAction(() ->
                sprite.animate().translationX(0).alpha(1f).setDuration(160).start()).start();
    }

    private String battleEffects(BattleMatch m, boolean player) {
        int shield = player ? m.playerShield : m.rivalShield;
        return (shield > 0 ? " · Escudo " + shield : "")
                + ((player ? m.playerDodge : m.rivalDodge) ? " · Esquiva" : "")
                + ((player ? m.playerWeak : m.rivalWeak) > 0 ? " · Enfraquecido" : "")
                + ((player ? m.playerBuff : m.rivalBuff) > 0 ? " · Fortalecido" : "")
                + ((player ? m.playerCrit : m.rivalCrit) ? " · Crítico pronto" : "")
                + ((player ? m.playerStunned : m.rivalStunned) ? " · Atordoado" : "");
    }

    private static class Question {
        final String prompt, fact;
        final String[] choices;
        final int answer;
        Question(String prompt, String a, String b, String c, int answer, String fact) {
            this.prompt = prompt; choices = new String[]{a, b, c}; this.answer = answer; this.fact = fact;
        }
    }

    private final Question[] questions = {
        new Question("Qual é o alter ego do Homem-Aranha?", "Peter Parker", "Steve Rogers", "Bruce Banner", 0, "Peter concilia sua vida pessoal com a responsabilidade de ser herói."),
        new Question("Quem empunha Mjölnir?", "Tony Stark", "Thor", "Clint Barton", 1, "Mjölnir é o martelo associado ao deus do trovão."),
        new Question("Qual equipe reúne os mutantes de Xavier?", "Vingadores", "Quarteto Fantástico", "X-Men", 2, "A escola de Xavier é uma base dos X-Men."),
        new Question("Quem é o Gigante Esmeralda?", "Hulk", "Visão", "Loki", 0, "Bruce Banner se transforma no Hulk."),
        new Question("Qual herói é Tony Stark?", "Homem de Ferro", "Homem-Formiga", "Falcão", 0, "Tony usa armaduras tecnológicas para combater ameaças."),
        new Question("Qual é o país do Pantera Negra?", "Latvéria", "Wakanda", "Asgard", 1, "Wakanda é uma nação conhecida por sua tecnologia e vibranium."),
        new Question("Qual metal está ligado a Wakanda?", "Cobre", "Ferro", "Vibranium", 2, "O vibranium é um recurso central de Wakanda."),
        new Question("Quem é o irmão adotivo de Thor?", "Loki", "Logan", "Scott", 0, "Loki é conhecido por seus truques e ilusões."),
        new Question("Qual equipe tem Groot e Rocket?", "X-Men", "Guardiões da Galáxia", "Defensores", 1, "Os Guardiões vivem aventuras espaciais."),
        new Question("Qual frase é associada a Groot?", "Avante!", "Hulk esmaga!", "Eu sou Groot", 2, "Groot se comunica com essa frase recorrente."),
        new Question("Quem é Steve Rogers?", "Capitão América", "Doutor Estranho", "Demolidor", 0, "Steve é um dos personagens que carregam o escudo do Capitão."),
        new Question("Qual herói usa arco e flechas?", "Visão", "Gavião Arqueiro", "Hulk", 1, "Clint Barton é conhecido pela precisão com o arco."),
        new Question("Quem é Natasha Romanoff?", "Tempestade", "Gamora", "Viúva Negra", 2, "Natasha é uma agente especializada em espionagem."),
        new Question("Qual herói protege o Sanctum Sanctorum?", "Doutor Estranho", "Wolverine", "Falcão", 0, "Stephen Strange pratica as artes místicas."),
        new Question("Qual é o nome de Wolverine?", "Peter", "Logan", "Reed", 1, "Logan também é conhecido por seu fator de cura."),
        new Question("Qual metal reveste as garras de Wolverine?", "Prata", "Vibranium", "Adamantium", 2, "Adamantium está ligado ao esqueleto e às garras de Logan."),
        new Question("Quem controla o clima nos X-Men?", "Tempestade", "Vampira", "Mística", 0, "Ororo Munroe é a Tempestade."),
        new Question("Quem dispara rajadas pelos olhos?", "Fera", "Ciclope", "Noturno", 1, "Scott Summers usa um visor para controlar as rajadas."),
        new Question("Qual mutante é conhecido por teletransporte?", "Colossus", "Magneto", "Noturno", 2, "Kurt Wagner se teletransporta para se deslocar."),
        new Question("Quem manipula campos magnéticos?", "Magneto", "Xavier", "Homem de Gelo", 0, "Magneto é um dos personagens centrais das histórias dos X-Men."),
        new Question("Qual é o poder característico de Reed Richards?", "Invisibilidade", "Elasticidade", "Telepatia", 1, "Reed é o Senhor Fantástico."),
        new Question("Quem é o Tocha Humana do Quarteto?", "Ben Grimm", "Reed Richards", "Johnny Storm", 2, "Johnny pode envolver seu corpo em chamas."),
        new Question("Quem é Ben Grimm?", "Coisa", "Fera", "Hulk", 0, "Ben faz parte do Quarteto Fantástico."),
        new Question("Quem é Susan Storm?", "Feiticeira Escarlate", "Mulher Invisível", "Capitã Marvel", 1, "Susan também cria campos de força."),
        new Question("Qual vilão governa a Latvéria?", "Thanos", "Ultron", "Doutor Destino", 2, "Victor von Doom é um adversário do Quarteto Fantástico."),
        new Question("Qual é o alter ego do Demolidor?", "Matt Murdock", "Bruce Banner", "Tony Stark", 0, "Matt trabalha como advogado."),
        new Question("Quem é Carol Danvers?", "Gamora", "Capitã Marvel", "Mística", 1, "Carol é uma heroína ligada a aventuras cósmicas."),
        new Question("Qual herói usa um traje para reduzir de tamanho?", "Thor", "Hulk", "Homem-Formiga", 2, "As partículas Pym permitem mudanças de tamanho."),
        new Question("Quem é conhecido como o Surfista Prateado?", "Norrin Radd", "Peter Quill", "Sam Wilson", 0, "Norrin viaja pelo espaço sobre uma prancha."),
        new Question("Qual é a identidade do Senhor das Estrelas?", "Clint Barton", "Peter Quill", "Scott Summers", 1, "Peter Quill integra os Guardiões da Galáxia.")
    };

    private void startQuiz() {
        LinearLayout screen = gameScreen("QUIZ MARVEL", "30 perguntas · heróis, equipes, poderes e lugares", "?", RED);
        screen.addView(label("As perguntas e as alternativas mudam a cada rodada. Acerte todas para conquistar o título de Portador da Sabedoria.", 14, MUTED, false));
        addAction(screen, "RODADA RÁPIDA · 5 PERGUNTAS", RED, () -> startQuiz(5));
        addAction(screen, "DESAFIO COMPLETO · 10 PERGUNTAS", RED, () -> startQuiz(10));
        showScreen(screen);
    }

    private void startQuiz(int length) {
        quizReview.clear();
        List<Question> pool = new ArrayList<>(Arrays.asList(questions));
        Collections.shuffle(pool, random);
        askQuestion(new ArrayList<>(pool.subList(0, length)), 0, 0);
    }

    private void askQuestion(List<Question> round, int index, int score) {
        LinearLayout screen = gameScreen("QUIZ MARVEL", "Conhecimento · " + round.size() + " perguntas", "?", RED);
        if (index >= round.size()) {
            if (score == round.size()) GamificationManager.unlockTitle(this, "wisdom");
            screen.addView(resultBanner(score * 5 >= round.size() * 4 ? "AGENTE DE ELITE" : "TREINO CONCLUÍDO",
                    "Você acertou " + score + " de " + round.size() + " (" + score * 100 / round.size() + "%)."));
            screen.addView(sectionLabel("REVISÃO DA RODADA"));
            for (String review : quizReview) {
                TextView item = label(review, 14, INK, false);
                item.setPadding(0, dp(7), 0, dp(10));
                screen.addView(item);
            }
            addAction(screen, "REPETIR DESAFIO", RED, () -> startQuiz(round.size()));
            addAction(screen, "ESCOLHER RODADA", RED, this::startQuiz);
            showScreen(screen); return;
        }
        Question question = round.get(index);
        TextView count = label("PERGUNTA " + (index + 1) + " / " + round.size() + " · ACERTOS " + score, 13, CYAN, true);
        count.setPadding(0, dp(12), 0, dp(12)); screen.addView(count);
        ProgressBar progress = progressBar(round.size(), CYAN);
        progress.setProgress(index); screen.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        TextView prompt = label(question.prompt, 22, INK, true);
        prompt.setPadding(0, dp(18), 0, dp(18)); screen.addView(prompt);
        List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2));
        Collections.shuffle(order, random);
        Button[] choices = new Button[3];
        TextView feedback = label("", 15, INK, false);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setPadding(0, dp(12), 0, dp(12));
        Button next = gameButton(index + 1 == round.size() ? "VER RESULTADO" : "PRÓXIMA PERGUNTA");
        next.setVisibility(View.GONE);
        boolean[] answered = {false};
        int[] selection = {-1};
        Button confirm = gameButton("CONFIRMAR RESPOSTA");
        confirm.setEnabled(false);
        for (int i = 0; i < 3; i++) {
            final int position = i, selected = order.get(i);
            Button choice = gameButton((char) ('A' + i) + "   " + question.choices[selected]);
            choices[i] = choice;
            LinearLayout.LayoutParams choiceParams = bottomMargin(choice, 9);
            choiceParams.topMargin = dp(6);
            screen.addView(choice, choiceParams);
            choice.setOnClickListener(v -> {
                if (answered[0]) return;
                selection[0] = position;
                for (int n = 0; n < choices.length; n++) {
                    choices[n].setText((n == position ? "● " : "○ ") + question.choices[order.get(n)]);
                    choices[n].setBackground(background(PANEL, PANEL, 15, n == position ? CYAN : 0xFF2A4258));
                    choices[n].setContentDescription(question.choices[order.get(n)] + (n == position ? ", selecionada" : ", não selecionada"));
                }
                confirm.setEnabled(true);
            });
        }
        confirm.setOnClickListener(v -> {
            if (answered[0] || selection[0] < 0) return;
            answered[0] = true;
            int selected = order.get(selection[0]);
            boolean correct = selected == question.answer;
            for (int n = 0; n < choices.length; n++) {
                Button option = choices[n]; option.setEnabled(false);
                if (order.get(n) == question.answer) {
                    option.setText("✓ " + question.choices[question.answer]);
                    option.setBackground(background(0xFF123E45, 0xFF123E45, 15, CYAN));
                } else if (n == selection[0]) {
                    option.setText("✕ " + question.choices[selected]);
                    option.setBackground(background(0xFF492432, 0xFF492432, 15, RED));
                }
            }
            animatePress(choices[selection[0]]);
            progress.setProgress(index + 1);
            feedback.setText((correct ? "Resposta certa! " : "Resposta correta: " + question.choices[question.answer] + ". ") + question.fact);
            quizReview.add((correct ? "✓ " : "✕ ") + question.prompt + "\nSua resposta: " + question.choices[selected]
                    + "\nCorreta: " + question.choices[question.answer] + ". " + question.fact);
            count.setText("RESPONDIDAS " + (index + 1) + " / " + round.size() + " · ACERTOS " + (score + (correct ? 1 : 0)));
            confirm.setVisibility(View.GONE); next.setVisibility(View.VISIBLE);
            next.setOnClickListener(w -> askQuestion(round, index + 1, score + (correct ? 1 : 0)));
        });
        LinearLayout.LayoutParams confirmParams = bottomMargin(confirm, 10);
        confirmParams.topMargin = dp(10);
        screen.addView(confirm, confirmParams);
        screen.addView(feedback);
        LinearLayout.LayoutParams nextParams = bottomMargin(next, 10);
        nextParams.topMargin = dp(10);
        screen.addView(next, nextParams);
        showScreen(screen);
    }

    private ProgressBar progressBar(int max, int color) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(max);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF263E54));
        return bar;
    }

    private boolean isActive(View content) {
        return !isFinishing() && !isDestroyed() && content.isAttachedToWindow();
    }

    private void startReflex() {
        LinearLayout screen = column();
        screen.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = gameButton("‹ SALA");
        back.setTextSize(12);
        top.addView(back, new LinearLayout.LayoutParams(dp(90), dp(48)));
        back.setOnClickListener(v -> showLobby());
        TextView heading = label("SENTIDO-ARANHA · 30s", 15, INK, true);
        heading.setGravity(Gravity.CENTER);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(top);
        screen.addView(label("Desvie para uma faixa livre após o aviso. Você tem três vidas.", 12, MUTED, false));
        TextView status = label("TEMPO 30s · VIDAS 3", 14, CYAN, true);
        status.setPadding(0, dp(3), 0, dp(3));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        screen.addView(status);
        ProgressBar progress = progressBar(30, 0xFFB79AFF);
        screen.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        TextView feedback = label("Título Reflexos do Multiverso: conclua a corrida e desvie em menos de 300 ms após um aviso.", 13, MUTED, false);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        SenseArena arena = new SenseArena(screen, status, feedback, progress);
        screen.addView(arena, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        for (int i = 0; i < 3; i++) {
            final int lane = i;
            Button button = gameButton(new String[]{"ESQUERDA", "CENTRO", "DIREITA"}[i]);
            button.setTextSize(11); button.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(54), 1);
            buttonParams.setMargins(dp(4), dp(8), dp(4), dp(8));
            controls.addView(button, buttonParams);
            button.setOnClickListener(v -> arena.moveTo(lane));
        }
        screen.addView(controls);
        screen.addView(feedback);
        Button action = gameButton("INICIAR");
        arena.action = action;
        screen.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));
        action.setOnClickListener(v -> {
            if (!arena.running) { arena.begin(); action.setText("PAUSAR"); }
            else if (arena.paused) { arena.userPaused = false; arena.resume(); action.setText("PAUSAR"); }
            else { arena.userPaused = true; arena.pause(); action.setText("CONTINUAR"); }
        });
        FrameLayout fixed = new FrameLayout(this);
        fixed.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        showScreen(fixed);
        senseArena = arena;
    }

    private class SenseArena extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LinearLayout screen;
        private final TextView status, feedback;
        private final ProgressBar progress;
        private int lane = 1, danger, secondDanger = -1, waves, lives = 3, fastDodges;
        private boolean running, paused, finished, reactionRecorded;
        private long lastFrame, warnedAt;
        private float elapsed, raceElapsed, protectedUntil;
        private boolean userPaused;
        private Button action;
        private String lastNotice = "";
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running || paused || !isActive(screen)) return;
                long now = SystemClock.uptimeMillis();
                long delta = Math.min(50, now - lastFrame);
                elapsed += delta; raceElapsed += now - lastFrame; lastFrame = now;
                progress.setProgress((int) (raceElapsed / 1000));
                String timer = "TEMPO " + Math.max(0, (int) Math.ceil((30000 - raceElapsed) / 1000f)) + "s · VIDAS " + lives;
                if (!timer.equals(status.getText().toString())) status.setText(timer);
                if (raceElapsed >= 30000) { finish(); return; }
                float travel = Math.max(650, 1500 - waves * 35);
                if (elapsed >= 650 + travel) {
                    if ((lane == danger || lane == secondDanger) && raceElapsed >= protectedUntil) {
                        lives--; protectedUntil = raceElapsed + 2400;
                        lastNotice = "IMPACTO! −1 vida. Proteção temporária. ";
                        animateHit(thisView(), false);
                    } else lastNotice = raceElapsed < protectedUntil ? "PROTEGIDO! " : "ESQUIVA! ";
                    waves++;
                    status.setText("TEMPO " + (int) Math.ceil((30000 - raceElapsed) / 1000f) + "s · VIDAS " + lives);
                    if (lives == 0) { finish(); return; }
                    newWave();
                }
                invalidate(); handler.postDelayed(this, 16);
            }
        };
        SenseArena(LinearLayout screen, TextView status, TextView feedback, ProgressBar progress) {
            super(GamesActivity.this);
            this.screen = screen; this.status = status; this.feedback = feedback; this.progress = progress;
            setContentDescription("Arena com três pistas. Use os botões abaixo para desviar.");
            setFocusable(true);
        }
        private View thisView() { return this; }
        void begin() {
            if (running || finished) return;
            running = true; newWave(); lastFrame = SystemClock.uptimeMillis(); handler.post(tick);
        }
        void newWave() {
            elapsed = 0; danger = random.nextInt(3); secondDanger = -1;
            if (waves >= 8 && waves % 3 == 2) secondDanger = (danger + 1 + random.nextInt(2)) % 3;
            warnedAt = SystemClock.uptimeMillis(); reactionRecorded = false;
            feedback.setText(lastNotice + "Perigo: " + laneName(danger)
                    + (secondDanger >= 0 ? " e " + laneName(secondDanger) : "") + ". Mude para uma faixa livre.");
        }
        String laneName(int lane) { return new String[]{"esquerda", "centro", "direita"}[lane]; }
        void moveTo(int destination) {
            if (finished || paused) return;
            boolean threatened = lane == danger || lane == secondDanger;
            lane = Math.max(0, Math.min(2, destination));
            if (running && threatened && lane != danger && lane != secondDanger && !reactionRecorded) {
                long reaction = SystemClock.uptimeMillis() - warnedAt;
                reactionRecorded = true;
                if (reaction < 300) fastDodges++;
                feedback.setText("Faixa livre · reação em " + reaction + " ms. Continue atento!");
            }
            invalidate();
        }
        void pause() { paused = true; handler.removeCallbacks(tick); invalidate(); }
        void resume() {
            if (!paused) return;
            paused = false; lastFrame = SystemClock.uptimeMillis();
            // Restart the warning after a background interruption so reaction time stays fair.
            if (running) { newWave(); handler.post(tick); }
        }
        void finish() {
            running = false; finished = true; handler.removeCallbacks(tick); invalidate();
            status.setText("FIM · " + lives + " vidas · " + waves + " ondas");
            if (lives > 0 && fastDodges > 0) GamificationManager.unlockTitle(GamesActivity.this, "reflex");
            feedback.setText((lives > 0 ? "ROTA CONCLUÍDA! 30 segundos" : "FIM DO TREINO · " + (int) (raceElapsed / 1000) + " segundos")
                    + " · " + fastDodges + " esquivas abaixo de 300 ms.");
            action.setText("JOGAR NOVAMENTE");
            action.setOnClickListener(v -> startReflex());
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                moveTo((int) (event.getX() * 3 / Math.max(1, getWidth())));
                performClick(); getParent().requestDisallowInterceptTouchEvent(false); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float width = getWidth(), height = getHeight(), track = width / 3;
            paint.setColor(NAVY); c.drawRect(0, 0, width, height, paint);
            if (paused) {
                paint.setColor(INK); paint.setTextSize(dp(18)); paint.setTextAlign(Paint.Align.CENTER);
                c.drawText("PAUSADO", width / 2, height / 2, paint);
            }
            if (raceElapsed < protectedUntil) {
                paint.setColor(INK); paint.setTextSize(dp(12)); paint.setTextAlign(Paint.Align.CENTER);
                c.drawText("PROTEGIDO", width / 2, height / 2, paint);
            }
            paint.setColor(0xFF38566B); paint.setStrokeWidth(dp(1));
            c.drawLine(track, 0, track, height, paint); c.drawLine(track * 2, 0, track * 2, height, paint);
            if (running || finished) {
                for (int bad : new int[]{danger, secondDanger}) {
                    if (bad < 0) continue;
                    if (elapsed < 650) {
                        paint.setColor(0x555F2038); c.drawRect(bad * track, 0, (bad + 1) * track, height, paint);
                        paint.setColor(0xFFFFD36C); paint.setTextSize(dp(28)); paint.setTextAlign(Paint.Align.CENTER);
                        c.drawText("!", (bad + .5f) * track, dp(38), paint);
                    } else {
                        float travel = Math.max(650, 1500 - waves * 35);
                        float y = ((elapsed - 650) / travel) * (height - dp(58));
                        float x = (bad + .5f) * track;
                        paint.setColor(RED); c.drawRoundRect(x - track * .28f, y, x + track * .28f, y + dp(32), dp(5), dp(5), paint);
                        paint.setColor(INK); paint.setStrokeWidth(dp(2));
                        c.drawLine(x - track * .18f, y + dp(7), x + track * .18f, y + dp(25), paint);
                        c.drawLine(x + track * .18f, y + dp(7), x - track * .18f, y + dp(25), paint);
                    }
                }
            }
            float scale = Math.min(track / 105, dp(74) / 132f);
        c.save(); c.translate((lane + .5f) * track - 50 * scale, height - 132 * scale - dp(6)); c.scale(scale, scale);
            drawAvatar(c, paint, RED, 2); c.restore();
        }
    }

    private void addRetry(LinearLayout screen, String text, Runnable action) {
        if (screen.findViewWithTag("retry") != null) return;
        Button retry = gameButton(text);
        retry.setTag("retry");
        LinearLayout.LayoutParams params = bottomMargin(retry, 10);
        params.topMargin = dp(10);
        screen.addView(retry, params);
        retry.setOnClickListener(v -> action.run());
        popIn(retry);
    }

    private LinearLayout gameScreen(String title, String subtitle, String symbol, int accent) {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(20), dp(18), dp(32));
        Button back = gameButton("‹   VOLTAR À SALA DE TREINO");
        back.setTextSize(13);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackground(background(0xFF0F2032, 0xFF0F2032, 14, 0xFF273F55));
        content.addView(back, bottomMargin(back, 12));
        back.setOnClickListener(v -> showLobby());

        LinearLayout scene = panel();
        scene.setBackground(background(0xFF142D46, 0xFF0B1B2D, 18, 0xFF2A465E));
        scene.setPadding(dp(10), dp(5), dp(10), dp(5));

        TextView heading = label(title, 18, accent, true);
        heading.setGravity(Gravity.CENTER);
        scene.addView(heading);
        TextView detail = label(subtitle, 12, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(4), 0, dp(3));
        scene.addView(detail);
        content.addView(scene, bottomMargin(scene, 10));
        return content;
    }

    private void showScreen(View screen) {
        handler.removeCallbacksAndMessages(null);
        if (senseArena != null) { senseArena.pause(); senseArena = null; }
        ViewGroup container = (ViewGroup) stage.getParent();
        if (container.getChildCount() > 0 && container.getChildAt(0) != stage) {
            container.getChildAt(0).setVisibility(screen instanceof ScrollView ? View.VISIBLE : View.GONE);
        }
        stage.removeAllViews();
        View displayed = screen;
        if (screen instanceof LinearLayout) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.addView(screen, new ScrollView.LayoutParams(-1, -2));
            displayed = scroll;
        }
        stage.addView(displayed, new FrameLayout.LayoutParams(-1, -1));
        displayed.setAlpha(0f);
        displayed.setTranslationY(dp(8));
        displayed.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
    }

    private LinearLayout resultBanner(String title, String message) {
        LinearLayout box = panel();
        box.setGravity(Gravity.CENTER);
        box.setBackground(background(0xFF123746, 0xFF102338, 18, CYAN));
        TextView headline = label(title, 17, CYAN, true);
        headline.setGravity(Gravity.CENTER);
        box.addView(headline);
        TextView body = label(message, 14, INK, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(7), 0, 0);
        box.addView(body);
        popIn(box);
        return box;
    }

    private void addAction(LinearLayout parent, String text, int accent, Runnable action) {
        Button button = gameButton(text);
        button.setTextColor(NAVY);
        button.setBackground(background(accent, accent, 15, accent));
        LinearLayout.LayoutParams params = bottomMargin(button, 10);
        params.topMargin = dp(10);
        parent.addView(button, params);
        button.setOnClickListener(v -> action.run());
    }

    private void flip(View view, String text) {
        view.animate().rotationY(85f).setDuration(90).withEndAction(() -> {
            if (view instanceof Button) ((Button) view).setText(text);
            view.setRotationY(-85f);
            view.animate().rotationY(0f).setDuration(90).start();
        }).start();
    }

    private Button gameButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(INK);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setSingleLine(false);
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setBackground(background(PANEL, PANEL, 15, 0xFF2A4258));
        return button;
    }

    private LinearLayout panel() {
        LinearLayout layout = column();
        layout.setPadding(dp(16), dp(15), dp(16), dp(15));
        layout.setBackground(background(PANEL, PANEL, 18, 0xFF263E54));
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView sectionLabel(String text) {
        TextView view = label(text, 11, CYAN, true);
        view.setPadding(dp(2), 0, 0, dp(10));
        return view;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int start, int end, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams bottomMargin(View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(margin);
        return params;
    }

    private void animatePress(View view) {
        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
    }

    private void popIn(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.94f);
        view.setScaleY(0.94f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
