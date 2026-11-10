// Keep macOS file panels attached to the foreground application window.
function focusFilePanel({app, window, platform=process.platform}) {
  if (platform === 'darwin' && app.focus) {
    app.focus({steal: true});
    if (window.isMinimized()) window.restore();
    window.moveTop();
    window.focus();
  }
}
function showOpenDialog(context, options) {
  focusFilePanel(context);
  return context.dialog.showOpenDialog(context.window, options);
}
function showSaveDialog(context, options) {
  focusFilePanel(context);
  return context.dialog.showSaveDialog(context.window, options);
}
module.exports = {showOpenDialog, showSaveDialog};
