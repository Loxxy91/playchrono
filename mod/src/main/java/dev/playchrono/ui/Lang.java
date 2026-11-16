package dev.playchrono.ui;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** In-game chat messages. The dashboard has its own (larger) translation table in web/i18n.js. */
public final class Lang {
    public static final String[] SUPPORTED = {"fr", "en", "es", "de", "it", "pt", "pl", "ru", "zh", "ja"};
    private static final Map<String, Map<String, String>> T = new HashMap<>();

    static {
        put("en", "Today: {0} played · {1}-day streak · {2} = your stats",
                "Milestone: {0} hours of play! ★",
                "You''ve been playing for {0}. Time for a short break?",
                "Welcome back! You were AFK for {0}.",
                "Opening your dashboard in the browser...",
                "Dashboard: {0}");
        put("fr", "Aujourd''hui : {0} de jeu · série de {1} jour(s) · {2} = tes stats",
                "Cap franchi : {0} heures de jeu ! ★",
                "Tu joues depuis {0}. Une petite pause ?",
                "Re ! Tu étais AFK depuis {0}.",
                "Ouverture du tableau de bord dans le navigateur...",
                "Tableau de bord : {0}");
        put("es", "Hoy: {0} jugado · racha de {1} día(s) · {2} = tus estadísticas",
                "¡Hito: {0} horas de juego! ★",
                "Llevas {0} jugando. ¿Un pequeño descanso?",
                "¡Bienvenido de nuevo! Estuviste AFK {0}.",
                "Abriendo tu panel en el navegador...",
                "Panel: {0}");
        put("de", "Heute: {0} gespielt · Serie: {1} Tag(e) · {2} = deine Statistiken",
                "Meilenstein: {0} Stunden Spielzeit! ★",
                "Du spielst seit {0}. Zeit für eine kurze Pause?",
                "Willkommen zurück! Du warst {0} AFK.",
                "Dein Dashboard wird im Browser geöffnet...",
                "Dashboard: {0}");
        put("it", "Oggi: {0} di gioco · serie di {1} giorno/i · {2} = le tue statistiche",
                "Traguardo: {0} ore di gioco! ★",
                "Stai giocando da {0}. Una piccola pausa?",
                "Bentornato! Sei stato AFK per {0}.",
                "Apertura della dashboard nel browser...",
                "Dashboard: {0}");
        put("pt", "Hoje: {0} jogados · sequência de {1} dia(s) · {2} = suas estatísticas",
                "Marco: {0} horas de jogo! ★",
                "Você está jogando há {0}. Que tal uma pausa?",
                "Bem-vindo de volta! Você ficou AFK por {0}.",
                "Abrindo seu painel no navegador...",
                "Painel: {0}");
        put("pl", "Dzisiaj: {0} gry · seria {1} dni · {2} = twoje statystyki",
                "Kamień milowy: {0} godzin gry! ★",
                "Grasz już {0}. Może krótka przerwa?",
                "Witaj z powrotem! Byłeś AFK przez {0}.",
                "Otwieranie panelu w przeglądarce...",
                "Panel: {0}");
        put("ru", "Сегодня: {0} в игре · серия: {1} дн. · {2} = ваша статистика",
                "Рубеж: {0} часов в игре! ★",
                "Вы играете уже {0}. Может, небольшой перерыв?",
                "С возвращением! Вы были AFK {0}.",
                "Открываю панель статистики в браузере...",
                "Панель: {0}");
        put("zh", "今天：已玩 {0} · 连续 {1} 天 · {2} = 查看统计",
                "里程碑：游戏时间达到 {0} 小时！★",
                "你已经连续玩了 {0}，休息一下吧？",
                "欢迎回来！你挂机了 {0}。",
                "正在浏览器中打开统计面板...",
                "统计面板：{0}");
        put("ja", "今日のプレイ: {0} · 連続 {1} 日 · {2} = 統計を見る",
                "達成: プレイ時間 {0} 時間！★",
                "{0} 連続でプレイ中です。少し休憩しませんか？",
                "おかえりなさい！{0} の間 AFK でした。",
                "ブラウザでダッシュボードを開いています...",
                "ダッシュボード: {0}");
    }

    static {
        T.get("en").put("lbInvite", "New: an online leaderboard! Press {0} > Leaderboard to join (optional).");
        T.get("fr").put("lbInvite", "Nouveau : un classement en ligne ! Appuie sur {0} > Classement pour participer (facultatif).");
        T.get("es").put("lbInvite", "¡Nuevo: una clasificación en línea! Pulsa {0} > Clasificación para participar (opcional).");
        T.get("de").put("lbInvite", "Neu: eine Online-Rangliste! Drücke {0} > Rangliste, um mitzumachen (optional).");
        T.get("it").put("lbInvite", "Novità: una classifica online! Premi {0} > Classifica per partecipare (facoltativo).");
        T.get("pt").put("lbInvite", "Novo: um ranking online! Pressione {0} > Ranking para participar (opcional).");
        T.get("pl").put("lbInvite", "Nowość: ranking online! Naciśnij {0} > Ranking, aby dołączyć (opcjonalnie).");
        T.get("ru").put("lbInvite", "Новинка: онлайн-рейтинг! Нажмите {0} > Рейтинг, чтобы участвовать (по желанию).");
        T.get("zh").put("lbInvite", "新功能：在线排行榜！按 {0} > 排行榜 即可加入（可选）。");
        T.get("ja").put("lbInvite", "新機能：オンラインランキング！{0} > ランキング から参加できます（任意）。");
    }

    private static void put(String lang, String welcome, String milestone, String brk, String afk, String dash, String dashFail) {
        Map<String, String> m = new HashMap<>();
        m.put("welcome", welcome);
        m.put("milestone", milestone);
        m.put("break", brk);
        m.put("afkReturn", afk);
        m.put("dashboard", dash);
        m.put("dashboardFail", dashFail);
        T.put(lang, m);
    }

    private Lang() {}

    /** "fr_ca" -> "fr", "pt_br" -> "pt", "zh_tw" -> "zh"; unknown -> "en". */
    public static String pick(String configured, String gameLanguage) {
        String l = configured == null || configured.isEmpty() || "auto".equalsIgnoreCase(configured) ? gameLanguage : configured;
        if (l == null) l = Locale.getDefault().getLanguage();
        l = l.toLowerCase(Locale.ROOT);
        int u = l.indexOf('_');
        if (u > 0) l = l.substring(0, u);
        return T.containsKey(l) ? l : "en";
    }

    public static String get(String lang, String key, Object... args) {
        Map<String, String> m = T.getOrDefault(lang, T.get("en"));
        String pattern = m.getOrDefault(key, T.get("en").get(key));
        return new MessageFormat(pattern, Locale.ROOT).format(args);
    }
}
