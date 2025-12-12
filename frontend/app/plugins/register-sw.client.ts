export default defineNuxtPlugin(() => {
    window.addEventListener('beforeinstallprompt', () => {
        console.log("### BEFOREINSTALLPROMPT FIRED ###");
    });

    if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register("/push-sw.js").catch(err => {
            console.error("Service worker registration failed", err);
        });
    }
});