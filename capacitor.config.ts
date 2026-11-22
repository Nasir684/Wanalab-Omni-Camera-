import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.wanalab.godeye',
  appName: 'GodEye - By WanaLab',
  webDir: 'www',
  backgroundColor: '#0B0E11',
  server: {
    androidScheme: 'https',
    cleartext: true
  },
  android: {
    allowMixedContent: true
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1500,
      backgroundColor: '#0B0E11',
      showSpinner: false
    }
  }
};

export default config;
