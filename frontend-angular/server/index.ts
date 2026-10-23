import { createApp } from './app.ts';
import { loadConfig, missingSettings } from './config.ts';

const config = loadConfig(process.env);
const missing = missingSettings(config);
if (missing.length > 0) {
  console.error(`Fehlende Konfiguration: ${missing.join(', ')}`);
  process.exit(1);
}

createApp(config).listen(config.port, () => {
  console.log(`Chronos BFF listening on :${config.port}, serving ${config.staticDir}`);
});
