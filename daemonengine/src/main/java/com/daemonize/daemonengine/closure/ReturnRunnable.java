package com.daemonize.daemonengine.closure;

import com.daemonize.daemonengine.utils.DaemonUtils;

public class ReturnRunnable<T> implements Runnable {

  private Closure<T> closure;
  private Return<T> ret;

  public ReturnRunnable(Closure<T> closure) {
    this.closure = closure;
    this.ret = new Return<>();
  }

  @SuppressWarnings("unchecked")
  public <K extends ReturnRunnable<T>> K setResult(T result) {
    ret.setResult(result);
    return (K) this;
  }

  @SuppressWarnings("unchecked")
  public <K extends ReturnRunnable> K setError(Exception error, String methodName) {
    ret.setError(
            error,
            "\nDaemon: "
                    + DaemonUtils.tag()
                    + "method '" + methodName + "' threw an exception:\n"
                    + error.getClass().getCanonicalName()
                    + ": "
                    + error.getMessage()
    );
    return (K) this;
  }

  @Override
  public void run() {
    closure.onReturn(ret);
  }

}
