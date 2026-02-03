export default defineNuxtPlugin(() => {
    window.addEventListener('beforeinstallprompt', () => {
        console.log("### BEFOREINSTALLPROMPT FIRED ###");
    });

    if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register("/push-sw.js").then(registration => {
            // Check for updates every 60 minutes
            setInterval(() => {
                registration.update();
            }, 60 * 60 * 1000);
        }).catch(err => {
            console.error("Service worker registration failed", err);
        });
    }
});