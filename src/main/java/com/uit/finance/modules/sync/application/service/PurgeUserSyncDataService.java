package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.in.PurgeUserSyncDataUseCase;
import com.uit.finance.modules.sync.application.port.out.OpLogPort;
import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PurgeUserSyncDataService implements PurgeUserSyncDataUseCase {

  private final OpLogPort opLog;
  private final SyncStatePort state;

  PurgeUserSyncDataService(OpLogPort opLog, SyncStatePort state) {
    this.opLog = opLog;
    this.state = state;
  }

  @Override
  public void purge(UserId owner) {
    opLog.deleteAllOf(owner);
    state.deleteState(owner);
  }
}
