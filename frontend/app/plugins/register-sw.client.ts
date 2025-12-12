export default defineNuxtPlugin(() => {
    if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register("/push-sw.js").catch(err => {
            console.error("Service worker registration failed", err);
        });
    }
});