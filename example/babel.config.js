module.exports = function(api) {
  api.cache(true);
  return {
    presets: ["babel-preset-expo"],
    plugins: [
      [
        "module-resolver",
        {
          root: ["."],
          alias: {
            "@origin": "./lib-origin/origin/src",
            "@native": "./lib-origin/roze/native",
            "@common": "./lib-origin/common",
            "@lib": "./lib-origin/roze/lib",
            "@roze": "./lib-origin/roze/src",
          },
        },
      ],
    ],
  };
};
