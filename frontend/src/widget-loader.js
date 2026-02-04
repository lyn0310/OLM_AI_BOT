import API_BASE_URL from './config';

const BASE_URL = API_BASE_URL.replace(/\/api$/, '');

const isDev = import.meta.env.MODE === 'development';

if (isDev) {
    console.log("[Loader] Dev Mode: Loading from Vite Server...");
    import(`${BASE_URL}/@vite/client`);
    import(`${BASE_URL}/src/main.jsx`);
} else {
    console.log(`[Loader] Prod Mode: Loading assets from ${BASE_URL}`);
    
    const script = document.createElement('script');
    script.src = `${BASE_URL}/assets/chatbot.js`;
    script.type = 'module';
    document.head.appendChild(script);

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `${BASE_URL}/assets/chatbot.css`;
    document.head.appendChild(link);
}