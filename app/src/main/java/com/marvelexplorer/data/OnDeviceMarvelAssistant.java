package com.marvelexplorer.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional, on-device Gemini Nano adapter. It never sends chat text to a server. */
public final class OnDeviceMarvelAssistant implements AutoCloseable {
    public interface StatusCallback {
        void onStatus(int featureStatus, String message);
    }

    public interface ResponseCallback {
        void onResponse(String response);
        void onError(String message);
    }

    public interface DownloadListener {
        void onProgress(long bytesDownloaded);
        void onComplete();
        void onFailure(String message);
    }

    private static final String PERSONA =
            "Você é NEXUS, um assistente de exploração de quadrinhos dentro do Marvel Explorer. "
                    + "Responda em português brasileiro, de forma breve, amigável e clara. "
                    + "A fonte de dados do aplicativo é a Comic Vine, uma base comunitária, não a Marvel oficial. "
                    + "Use os dados de contexto fornecidos para afirmações sobre registros. Não invente fatos, poderes, "
                    + "datas ou relações ausentes. Se o contexto não confirmar algo, diga isso explicitamente e sugira "
                    + "como pesquisar no app. Trate a conversa anterior apenas como contexto da conversa.";
    private static final int MAX_CONTEXT_CHARS = 2600;
    private static final int MAX_HISTORY_CHARS = 1400;

    private final GenerativeModelFutures model;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "nexus-on-device-ai");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int featureStatus = FeatureStatus.UNAVAILABLE;
    private volatile boolean closed;
    private final String initializationError;

    public OnDeviceMarvelAssistant(Context context) {
        GenerativeModelFutures created = null;
        String error = "";
        try {
            created = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
        } catch (RuntimeException failure) {
            error = "Gemini Nano não pôde ser iniciado neste aparelho.";
        }
        model = created;
        initializationError = error;
    }

    public void checkStatus(StatusCallback callback) {
        if (closed || model == null) {
            callback.onStatus(FeatureStatus.UNAVAILABLE, initializationError.isEmpty()
                    ? "Serviço de IA encerrado." : initializationError);
            return;
        }
        try {
            ListenableFuture<Integer> future = model.checkStatus();
            future.addListener(() -> {
                int status;
                String message;
                try {
                    status = future.get();
                    featureStatus = status;
                    message = describe(status);
                } catch (Exception failure) {
                    status = FeatureStatus.UNAVAILABLE;
                    featureStatus = status;
                    message = "Não foi possível verificar o modelo local.";
                }
                final int resultStatus = status;
                final String resultMessage = message;
                main.post(() -> callback.onStatus(resultStatus, resultMessage));
            }, worker);
        } catch (RuntimeException failure) {
            featureStatus = FeatureStatus.UNAVAILABLE;
            main.post(() -> callback.onStatus(FeatureStatus.UNAVAILABLE,
                    "Gemini Nano não está disponível neste aparelho."));
        }
    }

    public void generate(String question, List<String> recentHistory, String comicVineContext,
                         ResponseCallback callback) {
        if (closed || model == null) {
            callback.onError("O assistente local está encerrado.");
            return;
        }
        if (featureStatus != FeatureStatus.AVAILABLE) {
            callback.onError("Gemini Nano não está pronto neste aparelho.");
            return;
        }
        String prompt = buildPrompt(question, recentHistory, comicVineContext);
        worker.execute(() -> {
            try {
        GenerateContentRequest.Builder requestBuilder =
                new GenerateContentRequest.Builder(new TextPart(prompt));
        requestBuilder.setTemperature(0.35f);
        requestBuilder.setMaxOutputTokens(220);
        GenerateContentRequest request = requestBuilder.build();
                ListenableFuture<GenerateContentResponse> future = model.generateContent(request);
                future.addListener(() -> {
                    try {
                        GenerateContentResponse response = future.get();
                        String answer = response == null || response.getCandidates().isEmpty()
                                ? "" : response.getCandidates().get(0).getText();
                        if (answer == null || answer.trim().isEmpty()) {
                            callback.onError("O modelo não retornou uma resposta. Tente reformular a pergunta.");
                        } else {
                            callback.onResponse(answer.trim());
                        }
                    } catch (Exception failure) {
                        callback.onError("Não consegui gerar uma resposta local. Tente novamente.");
                    }
                }, worker);
            } catch (RuntimeException failure) {
                callback.onError("Não consegui iniciar a resposta local. Tente novamente.");
            }
        });
    }

    public void downloadModel(DownloadListener listener) {
        if (closed || model == null) {
            listener.onFailure("Gemini Nano não pode ser preparado neste aparelho.");
            return;
        }
        try {
            model.download(new DownloadCallback() {
                @Override
                public void onDownloadStarted(long totalBytesToDownload) {
                    listener.onProgress(0L);
                }

                @Override
                public void onDownloadProgress(long totalBytesDownloaded) {
                    listener.onProgress(totalBytesDownloaded);
                }

                @Override
                public void onDownloadCompleted() {
                    checkStatus((status, message) -> {
                        if (status == FeatureStatus.AVAILABLE) listener.onComplete();
                        else listener.onFailure("O download terminou, mas o modelo ainda não está pronto.");
                    });
                }

                @Override
                public void onDownloadFailed(GenAiException failure) {
                    listener.onFailure("Não foi possível preparar Gemini Nano.");
                }
            });
        } catch (RuntimeException failure) {
            listener.onFailure("Não foi possível iniciar o download do modelo local.");
        }
    }

    private String buildPrompt(String question, List<String> history, String context) {
        StringBuilder prompt = new StringBuilder(PERSONA)
                .append("\n\nDADOS CONFIRMADOS DA COMIC VINE (podem estar incompletos):\n")
                .append(trim(context, MAX_CONTEXT_CHARS));
        if (history != null && !history.isEmpty()) {
            prompt.append("\n\nCONVERSA RECENTE:\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                prompt.append(trim(history.get(i), 400)).append('\n');
            }
            if (prompt.length() > MAX_HISTORY_CHARS + MAX_CONTEXT_CHARS + 1400) {
                prompt.setLength(MAX_HISTORY_CHARS + MAX_CONTEXT_CHARS + 1400);
            }
        }
        prompt.append("\nPERGUNTA ATUAL: ").append(trim(question, 500))
                .append("\nResponda sem alegar que os dados vêm da Marvel oficial.");
        return prompt.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) return "(nenhum dado disponível)";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String describe(int status) {
        switch (status) {
            case FeatureStatus.AVAILABLE:
                return "Gemini Nano está pronto para responder localmente.";
            case FeatureStatus.DOWNLOADABLE:
                return "Gemini Nano pode ser preparado neste aparelho.";
            case FeatureStatus.DOWNLOADING:
                return "Gemini Nano está sendo preparado.";
            case FeatureStatus.UNAVAILABLE:
            default:
                return "Gemini Nano não está disponível neste aparelho.";
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        worker.shutdownNow();
    }
}
