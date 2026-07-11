export default defineNuxtPlugin(() => {
    navigator.serviceWorker.addEventListener('message', ({ data }) => {
        if (data.type === 'SESSION_EXPIRED') {
            console.log('session expired')
            navigateTo('/')
        }
    });
})